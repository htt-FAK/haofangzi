package com.zhq.haofangzi.engine.metric;

import com.zhq.haofangzi.engine.MetricCalculator;
import com.zhq.haofangzi.engine.MetricContext;
import com.zhq.haofangzi.engine.MetricResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 7 个代表指标计算器的落位（spec 004 §2 指标登记表；其余 14 个按 {@code specs/004/tasks.md}
 * T-069~T-074 分散到同包文件，保持"一个指标一个类"的可测试性）。
 *
 * <p>共同约定（见 {@link MetricCalculator}）：不抛异常、不返回 NaN、缺失即 insufficient。
 * 这里的类为包级私有，仅注册为 Bean，外部一律通过 {@code metric_code} 访问。
 */
final class Metrics {

    private Metrics() {}

    /** 展示用途：南向房间与北向房间投影在 x 轴的重叠下限（VENT_cross 阈值，答辩可调） */
    static final double CROSS_OVERLAP_MIN = 0.9d;

    static double r2(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}

/**
 * LIGHT_wfa 起居空间窗地比。
 * 公式：Σ(起居类房间窗面积) / Σ(起居类房间面积)；存在无窗起居室时 ×0.85 罚项。
 * 依据：《住宅项目规范》窗地面积比不低于 1/7（≈0.143）相关条文。
 */
@org.springframework.stereotype.Component
class LightWfaCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "LIGHT_wfa";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        List<com.zhq.haofangzi.domain.entity.Room> living = ctx.livingRooms();
        double area = living.stream().mapToDouble(r -> r.getArea().doubleValue()).sum();
        double win = living.stream().mapToDouble(r -> nvl(r.getWindowArea())).sum();
        if (living.isEmpty() || area <= 0d) {
            return MetricResult.insufficient(code(), "缺少起居空间面积/房间构件数据");
        }
        boolean darkLiving = living.stream().anyMatch(r -> !r.hasWindow());
        double wfa = win / area * (darkLiving ? 0.85d : 1d);
        if (Double.isNaN(wfa) || Double.isInfinite(wfa)) {
            return MetricResult.insufficient(code(), "数据格式异常（NaN/∞）");
        }
        return MetricResult.of(code(), Metrics.r2(wfa), BigDecimal.valueOf(wfa).setScale(3, RoundingMode.HALF_UP).toPlainString(),
                Map.of("起居窗面积", fmt(win) + "㎡", "起居地面面积", fmt(area) + "㎡",
                        "无窗起居室", darkLiving ? "有（×0.85）" : "无"));
    }

    private static double nvl(BigDecimal b) {
        return b == null ? 0d : b.doubleValue();
    }

    private static String fmt(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}

/**
 * LIGHT_orientation 主朝向评分输入。值域编码：S=4 SE/SW=3 E/W=2 N=1（分档按此序判定）。
 * 依据：住宅朝向与日照得热关系的行业设计常识。
 */
@org.springframework.stereotype.Component
class LightOrientationCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "LIGHT_orientation";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        String o = ctx.ht() == null ? null : ctx.ht().getOrientation();
        if (o == null || o.isBlank()) {
            return MetricResult.insufficient(code(), "户型朝向未录入");
        }
        double v = switch (o) {
            case "NS" -> 4.5d;                       // 南北最优（双向采光）
            case "S" -> 4d;
            case "SE", "SW" -> 3d;
            case "E", "W" -> 2d;
            default -> 1d;
        };
        return MetricResult.of(code(), v, o, Map.of("orientation", o));
    }
}

/**
 * VENT_cross 南北通透。简化算法：存在南向有窗房间与北向有窗房间，且二者在 x 轴投影重叠 ≥0.9m，
 * 即认为具备穿堂风路径（近似，见 docs/04 §4.2 与 ROADMAP"真实通风模拟"）。
 */
@org.springframework.stereotype.Component
class VentCrossCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "VENT_cross";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        var south = ctx.southWindowRooms();
        var north = ctx.northWindowRooms();
        if (ctx.ht() == null || ctx.rooms() == null || ctx.rooms().isEmpty()) {
            return MetricResult.insufficient(code(), "缺少房间构件数据");
        }
        boolean crossed = south.isEmpty() || north.isEmpty() ? false
                : south.stream().anyMatch(s -> north.stream().anyMatch(n -> overlap(s.xRange(), n.xRange()) >= Metrics.CROSS_OVERLAP_MIN));
        // 主朝向为 NS 时视为设计口径上的南北通透（人工标注优先）
        boolean nsByMeta = "NS".equals(ctx.ht().getOrientation());
        boolean result = crossed || nsByMeta;
        return MetricResult.bool(code(), result, result ? "南北通透" : "不通透",
                Map.of("南向有窗房间", String.valueOf(south.size()), "北向有窗房间", String.valueOf(north.size()),
                        "投影重叠≥" + Metrics.CROSS_OVERLAP_MIN + "m", String.valueOf(crossed)));
    }

    private static double overlap(double[] a, double[] b) {
        return Math.max(0d, Math.min(a[1], b[1]) - Math.max(a[0], b[0]));
    }
}

/** UTIL_usable_ratio 得房率 = 套内 / 建筑面积。依据：行业设计常识（公摊与使用效率）。 */
@org.springframework.stereotype.Component
class UtilUsableRatioCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "UTIL_usable_ratio";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        BigDecimal r = ctx.ht() == null ? null : ctx.ht().usableRatio();
        if (r == null) {
            return MetricResult.insufficient(code(), "缺少建筑面积/套内面积");
        }
        return MetricResult.of(code(), r.doubleValue(), r.toPlainString(),
                Map.of("套内", ctx.ht().getPrivateArea().toPlainString() + "㎡", "建筑面积", ctx.ht().getGfa().toPlainString() + "㎡"));
    }
}

/** CIRC_corridor_ratio 走道面积占比（越低越优）。依据：动线效率与设计常识。 */
@org.springframework.stereotype.Component
class CircCorridorRatioCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "CIRC_corridor_ratio";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.ht() != null && ctx.ht().getCorridorRatio() != null) {
            double v = ctx.ht().getCorridorRatio().doubleValue();
            return MetricResult.of(code(), v, BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).toPlainString(),
                    Map.of("来源", "户型主参数 corridor_ratio"));
        }
        double area = ctx.rooms() == null ? 0d : ctx.roomsOf("CORRIDOR").stream()
                .mapToDouble(r -> r.getArea().doubleValue()).sum();
        BigDecimal priv = ctx.ht() == null ? null : ctx.ht().getPrivateArea();
        if (priv == null || priv.signum() <= 0) {
            return MetricResult.insufficient(code(), "缺少套内面积");
        }
        double v = area / priv.doubleValue();
        return MetricResult.of(code(), Metrics.r2(v), BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).toPlainString(),
                Map.of("走道面积", area + "㎡"));
    }
}

/** QUIET_elevator_ratio 梯户比（每电梯服务的户数，越低越安静舒适）。 */
@org.springframework.stereotype.Component
class QuietElevatorRatioCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "QUIET_elevator_ratio";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.building() == null) {
            return MetricResult.insufficient(code(), "未绑定楼栋（可按需传 houseId 修正）");
        }
        double v = ctx.building().elevatorRatio();
        return MetricResult.of(code(), v, String.format("1梯%.1f户", v),
                Map.of("每层户数", String.valueOf(ctx.building().getUnitsPerFloor()),
                        "电梯数", String.valueOf(ctx.building().getElevatorCount())));
    }
}

/** GREEN_ceiling 室内净高（m）。依据：《住宅项目规范》净高不应低于 2.40m 等条文。 */
@org.springframework.stereotype.Component
class GreenCeilingCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "GREEN_ceiling";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        BigDecimal h = ctx.ht() == null ? null : ctx.ht().getCeilingHeight();
        if (h == null || h.signum() <= 0) {
            return MetricResult.insufficient(code(), "净高未录入");
        }
        return MetricResult.of(code(), h.doubleValue(), h.toPlainString() + "m", Map.of("ceiling_height", h.toPlainString()));
    }
}

/**
 * COST_total_fit 总价与预算匹配度。值 = 总价 ÷ 预算上限（≤1 落在预算内；1~1.1 轻微超；>1.1 明显超）。
 * 用户未填画像时 insufficient，不影响其它维度（FR-57）。刻意不给投资建议（宪法第三条）。
 */
@org.springframework.stereotype.Component
class CostTotalFitCalculator implements MetricCalculator {

    @Override
    public String code() {
        return "COST_total_fit";
    }

    @Override
    public MetricResult calc(MetricContext ctx) {
        if (ctx.profile() == null || !ctx.profile().hasBudget()) {
            return MetricResult.insufficient(code(), "画像未填预算区间（完善画像后可评）");
        }
        BigDecimal total = ctx.house() != null ? ctx.house().getTotalPrice()
                : (ctx.ht() == null ? null : ctx.ht().getPriceRef());
        if (total == null || total.signum() <= 0) {
            return MetricResult.insufficient(code(), "缺少参考总价/房源总价");
        }
        double ratio = total.doubleValue() / ctx.profile().budgetMax();
        return MetricResult.of(code(), Metrics.r2(ratio), String.format("%.0f万 / 预算上限%.0f万",
                        total.doubleValue() / 1e4, ctx.profile().budgetMax() / 1e4d),
                Map.of("总价", total.toPlainString(), "预算", ctx.profile().budgetMin() + "~" + ctx.profile().budgetMax()));
    }
}
