package com.zhq.haofangzi.common;

/** 业务错误码（与 plan.md §3.1、docs/02 §7 出错处理一一对应；新增须先登记再使用） */
public final class ErrorCode {

    private ErrorCode() {}

    public static final int OK = 0;
    // 400xx 参数
    public static final int PARAM_INVALID = 40001;
    public static final int PASSWORD_WEAK = 40010;
    public static final int CAPTCHA_ERROR = 40011;
    public static final int ROOM_OUT_OF_BOUND = 40012;   // 户型房间坐标越界（附 roomIndex，AC-15）
    public static final int NO_CONFIRM = 40013;
    public static final int BUDGET_RANGE = 40014;
    public static final int AREA_NOT_MATCH = 40015;
    public static final int COMPARE_SIZE = 40016;
    public static final int APPEND_MISSING = 40017;
    public static final int SLOT_FULL = 40018;
    public static final int DATE_CONFLICT = 40019;
    // 401xx / 403xx 认证鉴权
    public static final int UNAUTHORIZED = 40101;
    public static final int BAD_CREDENTIAL = 40301;
    public static final int FORBIDDEN = 40302;
    public static final int AI_DRAFT_NOT_CONFIRMED = 40303;
    public static final int SHARE_EXPIRED = 40304;
    public static final int LOGIN_LOCKED = 40305;
    // 404xx
    public static final int HOUSE_NOT_FOUND = 40410;
    public static final int HOUSE_TYPE_NOT_FOUND = 40420;
    // 409xx 业务冲突
    public static final int LOCK_CONFLICT = 40910;
    public static final int NOT_SELECTABLE = 40911;
    public static final int RENEW_LIMIT = 40912;
    public static final int PHONE_REGISTERED = 40913;
    public static final int FAVORITE_LIMIT = 40914;
    public static final int RULE_WEIGHT = 40920;
    public static final int RULE_PUBLISHED_READONLY = 40921;
    public static final int APPOINT_STATUS = 40931;
    public static final int SLOT_DISABLED = 40021;
    // 5xxxx
    public static final int INTERNAL = 50001;
    public static final int PDF_FAILED = 50002;
    public static final int AI_FALLBACK = 50310;
}
