package com.zhq.haofangzi.engine;

import java.util.List;

/**
 * 规则集视图（不可变，常驻缓存；对应 {@code eval_rule_set + eval_rule}，
 * 结构由 {@code specs/004-rule-scoring/contracts/scoring-rule.schema.json} 约束）。
 *
 * <p>为什么不可变：热更新时"整份替换"而不是原地修改（FR-58），
 * 使并发评估线程读到的一定是某个完整版本，避免半新半旧的分数（TC-P-03）。
 */
public record RuleSetView(long setId, String name, String version, String templateCode,
                          List<DimView> dims, LevelThresholds thresholds) {

    /** 维度视图：权重来自发布版本；模板覆写发生在 {@link ScoreAssembler}，不改本对象 */
    public record DimView(String code, String name, double weight, List<RuleView> rules) {
    }

    /**
     * 单条规则。
     *
     * @param metricCode     指标编码（必须能在 {@link MetricCalculator.Registry} 命中）
     * @param operator       LT/LE/GT/GE/BETWEEN/EQ/BOOL，仅用于展示判定表达式
     * @param internalWeight 维度内权重（Σ=1）
     * @param higherIsBetter 决定 best/worst 与分档排序方向
     * @param tiers          分档，<b>必须预先按命中顺序排好</b>（数值档左闭右开）
     * @param basis          依据条文（NFR-11，发布时必填）
     * @param suggestion     非最优档时的改进建议模板，支持 {value}/{need} 占位
     */
    public record RuleView(String metricCode, String metricName, String unit, String operator,
                           double internalWeight, boolean higherIsBetter, List<Tier> tiers,
                           String basis, String suggestion, List<String> sourceFields) {
    }

    /**
     * 一个分档。数值型用 min/max（[min,max)），布尔型用 boolValue（{@code value==1} 视为 true）。
     */
    public record Tier(Double min, Double max, Boolean boolValue, double score, String grade, String evidence) {

        public boolean hit(double v) {
            if (boolValue != null) {
                return boolValue == (v >= 0.5d);
            }
            if (min != null && v < min) {
                return false;
            }
            return max == null || v < max;
        }
    }

    public record LevelThresholds(double excellent, double good, double fair) {

        public static LevelThresholds defaults() {
            return new LevelThresholds(85, 75, 60);
        }

        public String levelOf(double total) {
            if (total >= excellent) {
                return "优";
            }
            if (total >= good) {
                return "良";
            }
            return total >= fair ? "中" : "差";
        }
    }

    /** 权重之和校验（FR-56：±0.001），保存 DRAFT 与装载 ACTIVE 时都要过一遍 */
    public boolean weightsValid() {
        double sum = dims.stream().mapToDouble(DimView::weight).sum();
        return Math.abs(sum - 1.0d) <= 0.001d;
    }
}
