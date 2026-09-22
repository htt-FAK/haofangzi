package com.zhq.haofangzi.engine.metric;

import com.zhq.haofangzi.domain.entity.Room;
import com.zhq.haofangzi.engine.MetricCalculator;
import com.zhq.haofangzi.engine.MetricContext;
import com.zhq.haofangzi.engine.MetricResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 规格登记表中其余指标（spec 004 plan §2）。与 {@link DefaultMetrics} 同一约定：
 * 不抛异常、不返回 NaN、缺字段即 {@code insufficient}。
 */
final class MoreMetrics {

    private MoreMetrics() {}

    static double nz(BigDecimal b) {
        return b == null ? Double.NaN : b.doubleValue();
    }

    static boolean bad(double v) {
        return Double.isNaN(v) || Double.isInfinite(v);
    }

    static String num(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    static double r3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}

/** LIGHT_bay_depth 面宽 / 进深，理想区间 1.2~1.8。 */
@org.springframework.stereotype.Component
class LightBayDepthCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "LIGHT_bay_depth";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.ht() == null || ctx.ht().getBay() == null || ctx.ht().getDepth() == null
                || ctx.ht().getDepth().signum() == 0) {
            return MetricResult.insufficient(code(), "缺少面宽或进深");
        }
        double v = ctx.ht().getBay().doubleValue() / ctx.ht().getDepth().doubleValue();
        if (MoreMetrics.bad(v)) {
            return MetricResult.insufficient(code(), "面宽进深比异常");
        }
        return MetricResult.of(code(), MoreMetrics.r3(v), MoreMetrics.num(v),
                Map.of("面宽", ctx.ht().getBay().toPlainString(), "进深", ctx.ht().getDepth().toPlainString()));
    }

}

/** LIGHT_floor_occlusion：1 - max(0,(8-floor)/8) × (1-southOcclusion)。无房源楼层则数据不足。 */
@org.springframework.stereotype.Component
class LightFloorOcclusionCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "LIGHT_floor_occlusion";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.house() == null || ctx.house().getFloorNo() == null
                || ctx.building() == null || ctx.building().getSouthOcclusion() == null) {
            return MetricResult.insufficient(code(), "未绑定楼层或南向遮挡系数");
        }
        int floor = ctx.house().getFloorNo();
        double occ = ctx.building().getSouthOcclusion().doubleValue();
        double v = 1d - Math.max(0d, (8d - floor) / 8d) * (1d - occ);
        if (MoreMetrics.bad(v)) {
            return MetricResult.insufficient(code(), "遮挡系数异常");
        }
        return MetricResult.of(code(), MoreMetrics.r3(v), MoreMetrics.num(v),
                Map.of("楼层", String.valueOf(floor), "南向无遮挡系数", String.valueOf(occ)));
    }

}

/** LIGHT_dark_bath 是否存在无外窗卫生间。true=有暗卫。 */
@org.springframework.stereotype.Component
class LightDarkBathCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "LIGHT_dark_bath";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        List<Room> baths = ctx.roomsOf("BATH");
        if (baths.isEmpty()) {
            return MetricResult.insufficient(code(), "缺少卫生间构件");
        }
        boolean dark = baths.stream().anyMatch(r -> !r.hasWindow());
        return MetricResult.bool(code(), dark, dark ? "存在暗卫" : "卫生间均有外窗",
                Map.of("卫生间数", String.valueOf(baths.size())));
    }
}

/** VENT_open_window_ratio 可开启窗面积 / 外窗面积。 */
@org.springframework.stereotype.Component
class VentOpenWindowRatioCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "VENT_open_window_ratio";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.rooms() == null || ctx.rooms().isEmpty()) {
            return MetricResult.insufficient(code(), "缺少房间构件");
        }
        double win = 0d;
        double open = 0d;
        for (Room r : ctx.rooms()) {
            if (r.getWindowArea() == null || r.getWindowArea().signum() <= 0) {
                continue;
            }
            double wa = r.getWindowArea().doubleValue();
            double ratio = r.getWindowOpenableRatio() == null ? 0.6d : r.getWindowOpenableRatio().doubleValue();
            win += wa;
            open += wa * ratio;
        }
        if (win <= 0d) {
            return MetricResult.insufficient(code(), "缺少外窗面积");
        }
        double v = open / win;
        return MetricResult.of(code(), MoreMetrics.r3(v), MoreMetrics.num(v), Map.of("外窗面积", win + "㎡"));
    }

}

/** VENT_kitch_bath_exhaust 厨卫均有外窗。 */
@org.springframework.stereotype.Component
class VentKitchBathExhaustCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "VENT_kitch_bath_exhaust";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        List<Room> wet = new java.util.ArrayList<>();
        wet.addAll(ctx.roomsOf("KITCHEN"));
        wet.addAll(ctx.roomsOf("BATH"));
        if (wet.isEmpty()) {
            return MetricResult.insufficient(code(), "缺少厨房或卫生间构件");
        }
        boolean ok = wet.stream().allMatch(Room::hasWindow);
        return MetricResult.bool(code(), ok, ok ? "厨卫均对外" : "存在无外窗厨卫",
                Map.of("厨卫房间数", String.valueOf(wet.size())));
    }
}

/** CIRC_wet_separation：卫生间面积 ≥4㎡ 且至少两个卫浴空间（独立洗手区）。 */
@org.springframework.stereotype.Component
class CircWetSeparationCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "CIRC_wet_separation";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.ht() == null) {
            return MetricResult.insufficient(code(), "缺少户型");
        }
        List<Room> baths = ctx.roomsOf("BATH");
        BigDecimal area = ctx.ht().getBathArea();
        if (area == null && baths.isEmpty()) {
            return MetricResult.insufficient(code(), "缺少卫生间面积");
        }
        double a = area == null ? baths.stream().mapToDouble(r -> r.getArea() == null ? 0d : r.getArea().doubleValue()).sum()
                : area.doubleValue();
        boolean ok = a >= 4d && baths.size() >= 2;
        return MetricResult.bool(code(), ok, ok ? "干湿分离" : "未形成独立卫浴分区",
                Map.of("卫生间面积", a + "㎡", "卫浴间数", String.valueOf(baths.size())));
    }
}

/** CIRC_public_private 主卧与客厅共墙重叠长度（米），越短越私密。 */
@org.springframework.stereotype.Component
class CircPublicPrivateCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "CIRC_public_private";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        List<Room> masters = ctx.roomsOf("MASTER");
        List<Room> livings = ctx.roomsOf("LIVING");
        if (masters.isEmpty() || livings.isEmpty()) {
            return MetricResult.insufficient(code(), "缺少主卧或客厅构件");
        }
        double overlap = 0d;
        for (Room m : masters) {
            for (Room l : livings) {
                overlap += shared(m, l);
            }
        }
        return MetricResult.of(code(), MoreMetrics.r3(overlap), MoreMetrics.num(overlap) + "m",
                Map.of("共墙重叠", MoreMetrics.num(overlap) + "m"));
    }

    static double shared(Room a, Room b) {
        if (a.getX() == null || a.getY() == null || a.getW() == null || a.getH() == null
                || b.getX() == null || b.getY() == null || b.getW() == null || b.getH() == null) {
            return 0d;
        }
        double ax1 = a.getX().doubleValue();
        double ax2 = ax1 + a.getW().doubleValue();
        double ay1 = a.getY().doubleValue();
        double ay2 = ay1 + a.getH().doubleValue();
        double bx1 = b.getX().doubleValue();
        double bx2 = bx1 + b.getW().doubleValue();
        double by1 = b.getY().doubleValue();
        double by2 = by1 + b.getH().doubleValue();
        double tol = 0.2d;
        double len = 0d;
        if (Math.abs(ax2 - bx1) <= tol || Math.abs(bx2 - ax1) <= tol) {
            len += Math.max(0d, Math.min(ay2, by2) - Math.max(ay1, by1));
        }
        if (Math.abs(ay2 - by1) <= tol || Math.abs(by2 - ay1) <= tol) {
            len += Math.max(0d, Math.min(ax2, bx2) - Math.max(ax1, bx1));
        }
        return len;
    }

}

/** CIRC_meal_kitchen 餐厅中心到厨房中心的距离（米）。 */
@org.springframework.stereotype.Component
class CircMealKitchenCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "CIRC_meal_kitchen";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        List<Room> dining = ctx.roomsOf("DINING");
        List<Room> kitchen = ctx.roomsOf("KITCHEN");
        if (dining.isEmpty() || kitchen.isEmpty()) {
            return MetricResult.insufficient(code(), "缺少餐厅或厨房构件");
        }
        double d = dist(dining.get(0), kitchen.get(0));
        if (MoreMetrics.bad(d)) {
            return MetricResult.insufficient(code(), "房间坐标不完整");
        }
        return MetricResult.of(code(), MoreMetrics.r3(d), MoreMetrics.num(d) + "m", Map.of("餐厨距离", MoreMetrics.num(d) + "m"));
    }

    static double dist(Room a, Room b) {
        if (a.getX() == null || a.getW() == null || a.getY() == null || a.getH() == null
                || b.getX() == null || b.getW() == null || b.getY() == null || b.getH() == null) {
            return Double.NaN;
        }
        double ax = a.getX().doubleValue() + a.getW().doubleValue() / 2d;
        double ay = a.getY().doubleValue() + a.getH().doubleValue() / 2d;
        double bx = b.getX().doubleValue() + b.getW().doubleValue() / 2d;
        double by = b.getY().doubleValue() + b.getH().doubleValue() / 2d;
        return Math.hypot(ax - bx, ay - by);
    }

}

/** UTIL_irregular 异形空间占比。 */
@org.springframework.stereotype.Component
class UtilIrregularCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "UTIL_irregular";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.ht() == null || ctx.ht().getIrrRatio() == null) {
            return MetricResult.insufficient(code(), "异形占比未录入");
        }
        double v = ctx.ht().getIrrRatio().doubleValue();
        return MetricResult.of(code(), v, MoreMetrics.num(v), Map.of("irr_ratio", MoreMetrics.num(v)));
    }
}

/** UTIL_room_min_size 起居空间最小开间（米）。低于 2.4m 记罚。 */
@org.springframework.stereotype.Component
class UtilRoomMinSizeCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "UTIL_room_min_size";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        double min = Double.POSITIVE_INFINITY;
        for (Room r : ctx.livingRooms()) {
            if (r.getW() == null || r.getH() == null) {
                continue;
            }
            min = Math.min(min, Math.min(r.getW().doubleValue(), r.getH().doubleValue()));
        }
        if (min == Double.POSITIVE_INFINITY) {
            return MetricResult.insufficient(code(), "缺少起居空间开间");
        }
        return MetricResult.of(code(), MoreMetrics.r3(min), MoreMetrics.num(min) + "m", Map.of("最小开间", MoreMetrics.num(min) + "m"));
    }

}

/** UTIL_storage 收纳墙长度 / 建筑面积。 */
@org.springframework.stereotype.Component
class UtilStorageCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "UTIL_storage";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.ht() == null || ctx.ht().getStorageWallLen() == null
                || ctx.ht().getGfa() == null || ctx.ht().getGfa().signum() <= 0) {
            return MetricResult.insufficient(code(), "缺少收纳墙长度或建筑面积");
        }
        double v = ctx.ht().getStorageWallLen().doubleValue() / ctx.ht().getGfa().doubleValue();
        return MetricResult.of(code(), MoreMetrics.r3(v), MoreMetrics.num(v),
                Map.of("收纳墙", ctx.ht().getStorageWallLen().toPlainString() + "m"));
    }

}

/** QUIET_road 卧室朝向是否与临路侧同向。true=临路。 */
@org.springframework.stereotype.Component
class QuietRoadCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "QUIET_road";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.ht() == null || ctx.ht().getNearRoad() == null) {
            return MetricResult.insufficient(code(), "临路标记未录入");
        }
        boolean near = ctx.ht().getNearRoad() == 1;
        return MetricResult.bool(code(), near, near ? "卧室临主干道" : "不临主干道", Map.of("near_road", String.valueOf(ctx.ht().getNearRoad())));
    }
}

/** QUIET_shaft 主卧是否贴电梯井或管井。 */
@org.springframework.stereotype.Component
class QuietShaftCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "QUIET_shaft";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.ht() == null || ctx.ht().getAdjacentElevator() == null) {
            return MetricResult.insufficient(code(), "贴井标记未录入");
        }
        boolean near = ctx.ht().getAdjacentElevator() == 1;
        return MetricResult.bool(code(), near, near ? "主卧贴电梯井" : "主卧不贴井",
                Map.of("adjacent_elevator", String.valueOf(ctx.ht().getAdjacentElevator())));
    }
}

/** GREEN_balcony 进深 ≥1.5m 的阳台数量；没有构件时退回 balcony_count。 */
@org.springframework.stereotype.Component
class GreenBalconyCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "GREEN_balcony";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.ht() == null) {
            return MetricResult.insufficient(code(), "缺少户型");
        }
        List<Room> bals = ctx.roomsOf("BALCONY");
        if (!bals.isEmpty()) {
            long good = bals.stream().filter(r -> r.getW() != null && r.getH() != null
                    && Math.min(r.getW().doubleValue(), r.getH().doubleValue()) >= 1.5d).count();
            return MetricResult.of(code(), good, good + " 个有效阳台", Map.of("阳台构件", String.valueOf(bals.size())));
        }
        if (ctx.ht().getBalconyCount() == null) {
            return MetricResult.insufficient(code(), "阳台数量未录入");
        }
        int n = ctx.ht().getBalconyCount();
        return MetricResult.of(code(), n, n + " 个阳台", Map.of("来源", "户型主参数 balcony_count"));
    }
}

/** GREEN_project_rate 楼栋绿化率。库中若为 35 表示 35%，换算到 0~1。 */
@org.springframework.stereotype.Component
class GreenProjectRateCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "GREEN_project_rate";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.building() == null || ctx.building().getGreenRate() == null) {
            return MetricResult.insufficient(code(), "绿化率未录入");
        }
        double v = ctx.building().getGreenRate().doubleValue();
        if (v > 1d) {
            v = v / 100d;
        }
        return MetricResult.of(code(), MoreMetrics.r3(v), MoreMetrics.num(v), Map.of("绿化率", MoreMetrics.num(v)));
    }

}

/** COST_unit_price_gap (单价 - 楼盘均价) / 均价。无房源单价时用参考总价 / 建筑面积。 */
@org.springframework.stereotype.Component
class CostUnitPriceGapCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "COST_unit_price_gap";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.project() == null || ctx.project().getAvgPrice() == null || ctx.project().getAvgPrice().signum() == 0) {
            return MetricResult.insufficient(code(), "缺少楼盘均价");
        }
        BigDecimal unit = ctx.house() == null ? null : ctx.house().getUnitPrice();
        if (unit == null && ctx.ht() != null && ctx.ht().getPriceRef() != null
                && ctx.ht().getGfa() != null && ctx.ht().getGfa().signum() > 0) {
            unit = ctx.ht().getPriceRef().divide(ctx.ht().getGfa(), 2, RoundingMode.HALF_UP);
        }
        if (unit == null || unit.signum() <= 0) {
            return MetricResult.insufficient(code(), "缺少单价");
        }
        double v = (unit.doubleValue() - ctx.project().getAvgPrice().doubleValue()) / ctx.project().getAvgPrice().doubleValue();
        if (MoreMetrics.bad(v)) {
            return MetricResult.insufficient(code(), "单价差计算异常");
        }
        return MetricResult.of(code(), MoreMetrics.r3(v), MoreMetrics.num(v),
                Map.of("单价", unit.toPlainString(), "楼盘均价", ctx.project().getAvgPrice().toPlainString()));
    }

}

/** COST_area_waste 走道占比 + 异形占比。 */
@org.springframework.stereotype.Component
class CostAreaWasteCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "COST_area_waste";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.ht() == null || (ctx.ht().getCorridorRatio() == null && ctx.ht().getIrrRatio() == null)) {
            return MetricResult.insufficient(code(), "缺少走道或异形占比");
        }
        double c = ctx.ht().getCorridorRatio() == null ? 0d : ctx.ht().getCorridorRatio().doubleValue();
        double i = ctx.ht().getIrrRatio() == null ? 0d : ctx.ht().getIrrRatio().doubleValue();
        double v = c + i;
        return MetricResult.of(code(), MoreMetrics.r3(v), MoreMetrics.num(v), Map.of("走道占比", String.valueOf(c), "异形占比", String.valueOf(i)));
    }

}
