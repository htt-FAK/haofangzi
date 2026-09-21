package com.zhq.haofangzi.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
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
                        .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/doc.html", "/favicon.ico").permitAll()
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "CONSULTANT")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/**").permitAll()
                        // 写接口一律需鉴权（NFR-06；分享只读页走 token 不过此处）
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/**").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource cors() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("http://localhost:*"));
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
            var now = java.time.Instant.now();
            return Jwts.builder().setSubject(String.valueOf(userId)).claim("role", role)
                    .setIssuedAt(java.util.Date.from(now))
                    .setExpiration(java.util.Date.from(now.plusSeconds(ttlSeconds)))
                    .signWith(key, SignatureAlgorithm.HS256).compact();
        }

        @SuppressWarnings("deprecation")
        public Map<String, Object> parse(String token) {
            var jws = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            return Map.of("sub", String.valueOf(jws.getSubject()), "role", String.valueOf(jws.get("role")));
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
                    if (claims != null && !claims.isEmpty()) {
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
