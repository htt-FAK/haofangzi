package com.zhq.haofangzi.service;

import com.zhq.haofangzi.config.HfProperties;
import com.zhq.haofangzi.config.SecurityConfig;
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

    @Bean
    public SecurityConfig.Jwt jwtBean() {
        return jwt;
    }
}
