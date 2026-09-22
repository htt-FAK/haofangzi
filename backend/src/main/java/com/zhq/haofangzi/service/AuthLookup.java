package com.zhq.haofangzi.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.config.HfProperties;
import com.zhq.haofangzi.config.SecurityConfig;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 令牌解析门面（spec 001 plan §2 的会话部分；T-003/T-007 会在此挂上黑名单与 refresh 一次性校验）。
 * 解析失败返回空 Map（不抛异常 → 交给 authorizeHttpRequests 决定是否放行）。
 */
@Slf4j
@Component
public class AuthLookup {

    private final SecurityConfig.Jwt jwt;
    private final Cache<String, Boolean> usedRefresh = Caffeine.newBuilder()
            .maximumSize(5000).expireAfterWrite(Duration.ofDays(7)).build();

    public AuthLookup(HfProperties props) {
        this.jwt = new SecurityConfig.Jwt(props);
    }

    public Map<String, Object> parseOrEmpty(String token) {
        try {
            return jwt.parse(token);
        } catch (Exception e) {
            log.debug("令牌无效：{}", e.getMessage());
            return Map.of();
        }
    }

    /** 签发给已认证用户（AuthController 登录成功时调用，T-005） */
    public String issue(long userId, String role) {
        return jwt.issue(userId, role);
    }

    public String issueRefresh(long userId, String role) {
        return jwt.issueRefresh(userId, role);
    }

    /** 刷新令牌只能换一次访问令牌（FR-03）。 */
    public String refreshOnce(String refreshToken) {
        Map<String, Object> claims = parseOrEmpty(refreshToken);
        if (claims.isEmpty() || !"refresh".equals(String.valueOf(claims.get("typ")))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "刷新令牌无效");
        }
        String jti = String.valueOf(claims.get("jti"));
        if (jti.isBlank() || usedRefresh.asMap().putIfAbsent(jti, Boolean.TRUE) != null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "刷新令牌已使用");
        }
        return issue(Long.parseLong(String.valueOf(claims.get("sub"))), String.valueOf(claims.get("role")));
    }

    @Bean
    public SecurityConfig.Jwt jwtBean() {
        return jwt;
    }
}
