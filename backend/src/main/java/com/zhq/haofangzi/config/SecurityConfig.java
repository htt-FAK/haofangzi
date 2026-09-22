package com.zhq.haofangzi.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import com.zhq.haofangzi.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 安全与可观测横切（宪法第四条 2/3；spec 001 plan §2）。
 *
 * <p>骨架口径：JWT 校验 + 角色前缀授权 + traceId 注入。
 * 登录/注册/锁定等由 {@code AuthController}（T-004~T-007）落地，届时把
 * {@code /api/auth/**}、{@code /doc.html} 加入白名单即可。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain chain(HttpSecurity http, JwtAuthFilter jwtFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(cors()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/doc.html", "/favicon.ico").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/share/**").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "CONSULTANT")
                        .requestMatchers("/api/ai/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/appointments/**", "/api/selection/**", "/api/users/**",
                                "/api/compare-reports/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/**").permitAll()
                        // 写接口一律需鉴权（NFR-06；分享只读页走 token 不过此处）
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> writeJson(res, ErrorCode.UNAUTHORIZED, "请先登录"))
                        .accessDeniedHandler((req, res, e) -> writeJson(res, ErrorCode.FORBIDDEN, "无权限执行此操作")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    private static void writeJson(HttpServletResponse res, int code, String message) throws IOException {
        res.setStatus(200);
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"traceId\":\"\",\"data\":null}");
    }

    private CorsConfigurationSource cors() {
        CorsConfiguration cfg = new CorsConfiguration();
        // 同时放行 localhost 与 127.0.0.1 来源：POST 等非 GET 请求会带 Origin 头，
        // 只配 localhost 时用 127.0.0.1 访问会被 CORS 过滤器拦截（GET 不发 Origin 故表现正常）
        cfg.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    /** HS256 无状态令牌；密钥只在环境变量（宪法第七条）。T-007 会补 refresh 与失效策略。 */
    public static class Jwt {

        private final SecretKey key;
        private final long ttlSeconds;

        public Jwt(HfProperties props) {
            this.key = Keys.hmacShaKeyFor(props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
            this.ttlSeconds = props.getJwt().getTtlSeconds();
        }

        public String issue(long userId, String role) {
            return sign(userId, role, "access", ttlSeconds, null);
        }

        /** 一次性刷新令牌，有效期 7 天。用过即作废（FR-03）。 */
        public String issueRefresh(long userId, String role) {
            return sign(userId, role, "refresh", 7 * 24 * 3600L, java.util.UUID.randomUUID().toString());
        }

        private String sign(long userId, String role, String typ, long seconds, String jti) {
            var now = java.time.Instant.now();
            var b = Jwts.builder().setSubject(String.valueOf(userId)).claim("role", role).claim("typ", typ)
                    .setIssuedAt(java.util.Date.from(now))
                    .setExpiration(java.util.Date.from(now.plusSeconds(seconds)));
            if (jti != null) {
                b.setId(jti);
            }
            return b.signWith(key, SignatureAlgorithm.HS256).compact();
        }

        @SuppressWarnings("deprecation")
        public Map<String, Object> parse(String token) {
            var jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            String typ = jws.get("typ") == null ? "access" : String.valueOf(jws.get("typ"));
            String jti = jws.getId() == null ? "" : jws.getId();
            return Map.of("sub", String.valueOf(jws.getSubject()), "role", String.valueOf(jws.get("role")),
                    "typ", typ, "jti", jti);
        }
    }

    /** 把 Bearer 令牌换成 {@code Principal}（name=userId），并把 traceId 放进 MDC 供日志使用（NFR-09） */
    static class JwtAuthFilter extends OncePerRequestFilter {

        private final com.zhq.haofangzi.service.AuthLookup authLookup;
        private final Cache<String, Map<String, Object>> parsed = Caffeine.newBuilder()
                .maximumSize(2000).expireAfterWrite(Duration.ofMinutes(5)).build();

        JwtAuthFilter(com.zhq.haofangzi.service.AuthLookup authLookup) {
            this.authLookup = authLookup;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            MDC.put("traceId", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            try {
                String h = req.getHeader("Authorization");
                if (h != null && h.startsWith("Bearer ")) {
                    Map<String, Object> claims = parsed.get(h.substring(7), authLookup::parseOrEmpty);
                    if (claims != null && !claims.isEmpty() && !"refresh".equals(String.valueOf(claims.get("typ")))) {
                        String role = String.valueOf(claims.get("role"));
                        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                String.valueOf(claims.get("sub")), null,
                                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_" + role)));
                        org.springframework.security.core.context.SecurityContextHolder.getContext()
                                .setAuthentication(auth);
                    }
                }
                chain.doFilter(req, res);
            } finally {
                MDC.remove("traceId");
            }
        }
    }

    @Bean
    JwtAuthFilter jwtAuthFilter(com.zhq.haofangzi.service.AuthLookup lookup) {
        return new JwtAuthFilter(lookup);
    }
}
