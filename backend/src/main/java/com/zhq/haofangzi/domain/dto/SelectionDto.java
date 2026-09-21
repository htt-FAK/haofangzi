package com.zhq.haofangzi.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/** 选房/锁房/收藏传输对象（openapi 的 LockCmd / LockResultVO / FavoriteCmd） */
public final class SelectionDto {

    private SelectionDto() {}

    /** POST /api/selection/locks（FR-34：confirm 必须由前端勾选，否则 40013） */
    @Data
    public static class LockCmd {
        @NotNull(message = "房源必填")
        private Long houseId;
        private boolean confirm;
    }

    @Data
    public static class LockResult {
        private String lockNo;
        private String intentionNo;
        /** 服务端时间基准，前端倒计时以此为准（AC-22） */
        private String expireAt;
        private int renewCount;
        private long remainSeconds;
        private HouseSnap snapshot;
        /** 冲突时返回（FR-41/AC-20） */
        private List<HouseSnap> alternatives;

        public static LockResult of(String lockNo, String intentNo, java.time.LocalDateTime expireAt,
                                   int renew, com.zhq.haofangzi.domain.entity.House h) {
            LockResult r = new LockResult();
            r.lockNo = lockNo;
            r.intentionNo = intentNo;
            r.expireAt = expireAt.toString();
            r.renewCount = renew;
            r.remainSeconds = Math.max(0, java.time.Duration.between(java.time.LocalDateTime.now(), expireAt).getSeconds());
            r.snapshot = HouseSnap.of(h);
            return r;
        }
    }

    /** 房源快照（确认页展示，避免锁定后价格漂移引起纠纷） */
    @Data
    public static class HouseSnap {
        private Long houseId;
        private String buildingCode;
        private Integer floorNo;
        private String roomNo;
        private String area;
        private String totalPriceWan;
        private String houseTypeName;
        private String saleStatus;

        public static HouseSnap of(com.zhq.haofangzi.domain.entity.House h) {
            HouseSnap s = new HouseSnap();
            if (h == null) {
                return s;
            }
            s.houseId = h.getId();
            s.floorNo = h.getFloorNo();
            s.roomNo = h.getRoomNo();
            s.area = h.getArea() == null ? null : h.getArea().toPlainString() + "㎡";
            s.totalPriceWan = h.getTotalPrice() == null ? null
                    : h.getTotalPrice().divide(java.math.BigDecimal.valueOf(10000), 2, java.math.RoundingMode.HALF_UP).toPlainString() + " 万";
            s.saleStatus = h.getSaleStatus();
            return s;
        }
    }
}
