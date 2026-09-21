package com.zhq.haofangzi.common;

/** PII 脱敏（NFR-06/13；FR-102、AC-46/57）：所有出口 VO、日志、PDF、Excel、AI payload 一律走这里 */
public final class MaskUtil {

    private MaskUtil() {}

    /** 138****0001；长度不足 11 或为空时返回 "***" */
    public static String mobile(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone == null ? null : "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 昵称脱敏：保留首字符 */
    public static String nickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return nickname;
        }
        return nickname.charAt(0) + "**";
    }

    /** 供 AI 载荷白名单兜底：清除任何 11 位手机号形态字符串（FR-122） */
    public static String scrub(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("1[3-9]\\d{9}", "1**********")
                   .replaceAll("\\d{17}[\\dXx]", "****************")
                   .replaceAll("[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}", "***@***");
    }
}
