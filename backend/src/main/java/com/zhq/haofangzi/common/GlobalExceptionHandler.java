package com.zhq.haofangzi.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一异常出口（docs/03 §7）：任何异常都不返回裸 5xx，前端按 code 分流 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Map<String, Object>> biz(BizException e, HttpServletRequest req) {
        log.warn("业务异常 {} {} code={} msg={}", req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        Map<String, Object> data = new LinkedHashMap<>();
        Object payload = e.getPayload();
        if (payload instanceof Map<?, ?> map) {
            map.forEach((k, v) -> data.put(String.valueOf(k), v));
            data.put("detail", map);
        } else if (payload instanceof java.util.List<?> list) {
            data.put("alternatives", list);
            data.put("detail", list);
        } else if (payload != null) {
            data.put("detail", payload);
        }
        return ApiResponse.fail(e.getCode(), e.getMessage(), data.isEmpty() ? null : data);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> invalid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数不合法" : fe.getField() + " " + fe.getDefaultMessage();
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> unreadable(HttpMessageNotReadableException e) {
        return ApiResponse.fail(ErrorCode.PARAM_INVALID, "请求体格式错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> denied(AccessDeniedException e) {
        return ApiResponse.fail(ErrorCode.FORBIDDEN, "无权限执行此操作");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> unknown(Exception e, HttpServletRequest req) {
        log.error("未处理异常 {} {}", req.getMethod(), req.getRequestURI(), e);   // 日志含堆栈，PII 已在 VO 层脱敏
        return ApiResponse.fail(ErrorCode.INTERNAL, "服务内部错误，请联系演示人员并提供 traceId");
    }
}
