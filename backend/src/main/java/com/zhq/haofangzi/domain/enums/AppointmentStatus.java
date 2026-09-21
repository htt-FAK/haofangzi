package com.zhq.haofangzi.domain.enums;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 预约单状态机（spec 006 FR-93；图 {@code docs/diagrams/appointment-state.mmd}，判定表 docs/04 §6.1）。
 *
 * <p>{@link #assertCan} 是唯一准入闸门：任何"跳过状态"的操作都在 service 入口被拒（返回 40931），
 * 前端隐藏入口只是体验优化，不作为安全边界（AC-52 双重防护）。
 */
public enum AppointmentStatus {

    PENDING("待确认"), CONFIRMED("已确认"), ARRIVED("已到场"), COMPLETED("已完成"), CANCELED("已取消");

    private final String label;

    AppointmentStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> NEXT = new EnumMap<>(AppointmentStatus.class);

    static {
        NEXT.put(PENDING, EnumSet.of(CONFIRMED, CANCELED));
        NEXT.put(CONFIRMED, EnumSet.of(ARRIVED, CANCELED));
        NEXT.put(ARRIVED, EnumSet.of(COMPLETED));
        NEXT.put(COMPLETED, EnumSet.noneOf(AppointmentStatus.class));
        NEXT.put(CANCELED, EnumSet.noneOf(AppointmentStatus.class));
    }

    public boolean canNext(AppointmentStatus to) {
        return to != null && NEXT.get(this).contains(to);
    }

    /** 占位状态：占用时段容量（用于 used 统计与同日冲突判定 FR-94） */
    public boolean occupies() {
        return this == PENDING || this == CONFIRMED;
    }

    /** 用户自助可操作的状态（改期/取消），到场后不允许再取消 */
    public boolean userEditable() {
        return this == PENDING || this == CONFIRMED;
    }

    public AppointmentStatus requireNext(AppointmentStatus to) {
        if (!canNext(to)) {
            throw new com.zhq.haofangzi.common.BizException(
                    com.zhq.haofangzi.common.ErrorCode.APPOINT_STATUS,
                    "预约状态不允许从「" + label + "」变更为「" + (to == null ? "?" : to.label) + "」");
        }
        return to;
    }

    public static AppointmentStatus of(String code) {
        for (AppointmentStatus s : values()) {
            if (s.name().equalsIgnoreCase(code)) {
                return s;
            }
        }
        throw new com.zhq.haofangzi.common.BizException(
                com.zhq.haofangzi.common.ErrorCode.PARAM_INVALID, "未知预约状态：" + code);
    }
}
