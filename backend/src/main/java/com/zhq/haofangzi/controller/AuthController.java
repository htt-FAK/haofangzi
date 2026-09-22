package com.zhq.haofangzi.controller;

import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.service.AuthService;
import java.security.Principal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 认证与当前用户（spec 001；openapi tag auth） */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    @GetMapping("/auth/captcha")
    public ApiResponse<Map<String, Object>> captcha() {
        return ApiResponse.ok(auth.captcha());
    }

    @PostMapping("/auth/code")
    public ApiResponse<Void> code(@RequestBody Map<String, String> body) {
        auth.sendSms(body.get("phone"));
        return ApiResponse.ok();
    }

    @PostMapping("/auth/register")
    public ApiResponse<Void> register(@RequestBody Map<String, String> body) {
        auth.register(body);
        return ApiResponse.ok();
    }

    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(auth.login(body));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok();
    }

    @PostMapping("/auth/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(auth.refresh(body.get("refreshToken")));
    }

    @GetMapping("/users/me")
    public ApiResponse<Map<String, Object>> me(Principal me) {
        if (me == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(auth.me(Long.parseLong(me.getName())));
    }

    @PutMapping("/users/me/profile")
    public ApiResponse<Map<String, Object>> profile(@RequestBody Map<String, Object> body, Principal me) {
        if (me == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return ApiResponse.ok(auth.updateProfile(Long.parseLong(me.getName()), body));
    }
}
