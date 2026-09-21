package com.zhq.haofangzi.common;

import lombok.Getter;

/** 业务异常：由 service 抛出，{@link GlobalExceptionHandler} 转 {@link ApiResponse}（HTTP 200 + code） */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final transient Object payload;   // 冲突时携带备选、剩余秒数等（AC-20/AC-34/AC-01）

    public BizException(int code, String message) {
        this(code, message, null);
    }

    public BizException(int code, String message, Object payload) {
        super(message);
        this.code = code;
        this.payload = payload;
    }

    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }
}
