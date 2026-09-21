package com.zhq.haofangzi.common;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

/**
 * 统一响应包装（plan.md §3.1 / NFR-09）。全部接口返回 HTTP 200 + 业务 code，
 * 便于前端拦截器按 code 分流（401xx 跳登录、40910 弹备选、50310 显示降级提示条）。
 *
 * @param <T> 业务负载
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 0=成功；其余见错误码表 */
    private int code;
    private String message;
    private String traceId;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "OK", trace(), data);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    /** AI 降级：code=50310，data 仍可用（宪法 4.6 / AC-62） */
    public static <T> ApiResponse<T> fallback(T data, String reason) {
        return new ApiResponse<>(50310, "AI 暂不可用，已使用模板结果：" + reason, trace(), data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, trace(), null);
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, trace(), data);
    }

    private static String trace() {
        String t = MDC.get("traceId");
        return t != null ? t : UUID.randomUUID().toString().replace("-", "");
    }
}
