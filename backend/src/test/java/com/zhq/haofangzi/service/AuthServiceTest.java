package com.zhq.haofangzi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.config.HfProperties;
import com.zhq.haofangzi.domain.entity.SysUser;
import com.zhq.haofangzi.mapper.UserMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    static final String SEED_HASH = "$2a$10$AeprUosNCZBbF6fe38qX1.tN9rJWCGIU0pjVR3E34W301IXE0aX2m";

    @Mock
    UserMapper users;
    @Mock
    AuthLookup tokens;
    PasswordEncoder encoder = new BCryptPasswordEncoder(10);
    AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, tokens, encoder, new HfProperties());
    }

    @Test
    @DisplayName("种子口令 Test@123 能通过 BCrypt 校验；否则需更新 seed-data.sql")
    void seedPasswordHash() {
        boolean ok = encoder.matches("Test@123", SEED_HASH);
        if (!ok) {
            System.out.println("请把 seed-data.sql 中的散列替换为: " + encoder.encode("Test@123"));
        }
        assertThat(ok).isTrue();
    }

    @Test
    @DisplayName("口令登录成功签发 JWT")
    void loginSuccess() {
        SysUser u = buyer();
        when(users.userByPhone("13800000001")).thenReturn(u);
        when(tokens.issue(1L, "BUYER")).thenReturn("jwt-token");
        Map<String, Object> cap = auth.captcha();
        Map<String, Object> data = auth.login(Map.of(
                "phone", "13800000001",
                "password", "Test@123",
                "captchaId", String.valueOf(cap.get("captchaId")),
                "captcha", solve(cap)));
        assertThat(data.get("token")).isEqualTo("jwt-token");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) data.get("user");
        assertThat(user.get("role")).isEqualTo("BUYER");
        assertThat(user.get("profileCompleteness")).isEqualTo(0);
    }

    @Test
    @DisplayName("连续失败达到上限后返回 40305")
    void lockAfterFails() {
        when(users.userByPhone("13800000001")).thenReturn(buyer());
        for (int i = 0; i < 4; i++) {
            Map<String, Object> cap = auth.captcha();
            assertThatThrownBy(() -> auth.login(Map.of(
                    "phone", "13800000001", "password", "wrong-pass1",
                    "captchaId", String.valueOf(cap.get("captchaId")),
                    "captcha", solve(cap))))
                    .isInstanceOf(BizException.class)
                    .extracting(ex -> ((BizException) ex).getCode())
                    .isEqualTo(ErrorCode.BAD_CREDENTIAL);
        }
        Map<String, Object> cap = auth.captcha();
        assertThatThrownBy(() -> auth.login(Map.of(
                "phone", "13800000001", "password", "wrong-pass1",
                "captchaId", String.valueOf(cap.get("captchaId")),
                "captcha", solve(cap))))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException be = (BizException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.LOGIN_LOCKED);
                    assertThat(((Map<?, ?>) be.getPayload()).get("lockSeconds")).isEqualTo(900L);
                });
    }

    @Test
    @DisplayName("重复手机号注册 40913")
    void registerDuplicate() {
        when(users.userByPhone("13900000000")).thenReturn(buyer());
        Map<String, Object> cap = auth.captcha();
        assertThatThrownBy(() -> auth.register(Map.of(
                "phone", "13900000000", "password", "Test@123",
                "captchaId", String.valueOf(cap.get("captchaId")),
                "captcha", solve(cap))))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PHONE_REGISTERED);
    }

    @Test
    @DisplayName("注册成功写入 BCrypt 散列")
    void registerOk() {
        when(users.userByPhone("13900001111")).thenReturn(null);
        Map<String, Object> cap = auth.captcha();
        auth.register(Map.of(
                "phone", "13900001111", "password", "Test@123", "nickname", "小王",
                "captchaId", String.valueOf(cap.get("captchaId")),
                "captcha", solve(cap)));
        verify(users).insertUser(any(SysUser.class));
    }

    @Test
    @DisplayName("口令只有数字时拒绝注册")
    void weakPassword() {
        Map<String, Object> cap = auth.captcha();
        assertThatThrownBy(() -> auth.register(Map.of(
                "phone", "13900002222", "password", "12345678",
                "captchaId", String.valueOf(cap.get("captchaId")),
                "captcha", solve(cap))))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PASSWORD_WEAK);
    }

    private SysUser buyer() {
        SysUser u = new SysUser();
        u.setId(1L);
        u.setPhone("13800000001");
        u.setPassword(encoder.encode("Test@123"));
        u.setNickname("小张");
        u.setRole("BUYER");
        u.setStatus(1);
        return u;
    }

    private String solve(Map<String, Object> cap) {
        return auth.peekCaptcha(String.valueOf(cap.get("captchaId")));
    }
}
