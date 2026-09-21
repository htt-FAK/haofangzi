package com.zhq.haofangzi.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 房源销控状态机（spec 003 FR-39；图 {@code docs/diagrams/house-state.mmd}）。
 * 集中定义合法流转，service 层任何流转都走 {@link #canNext}，非法即 40911（AC-27）。
 */
public enum SaleStatus {

    AVAILABLE, LOCKED, RESERVED, SOLD;

    private static final java.util.Map<SaleStatus, Set<SaleStatus>> ALLOWED = new java.util.EnumMap<>(SaleStatus.class);

    static {
        ALLOWED.put(AVAILABLE, EnumSet.of(LOCKED, SOLD));
        ALLOWED.put(LOCKED, EnumSet.of(AVAILABLE, RESERVED, SOLD));
        ALLOWED.put(RESERVED, EnumSet.of(AVAILABLE, SOLD));
        ALLOWED.put(SOLD, EnumSet.noneOf(SaleStatus.class));
    }

    public boolean canNext(SaleStatus to) {
        return to != null && ALLOWED.get(this).contains(to);
    }

    /** 仅"可选"允许被锁定（AC-27：SOLD/LOCKED 一律拒绝） */
    public boolean selectable() {
        return this == AVAILABLE;
    }
}
