package com.zhq.haofangzi.engine;

import java.util.Map;

/**
 * 单个指标的计算结果（spec 004 plan §1）。
 *
 * <p>契约要点：计算器<b>永不抛异常</b>——任何异常或输入缺失都必须返回
 * {@link #insufficient(String, String)}，由 {@link ScoreAssembler} 按"数据不足"做维度内权重归一
 * （FR-57 / AC-31），保证一条坏数据不会打挂整个评估。
 *
 * @param code     指标编码，如 {@code LIGHT_wfa}，须与 {@code eval_rule.metric_code} 一致
 * @param value    指标值；{@code null} 表示数据不足
 * @param ok       是否有有效值
 * @param display  给人看的值文本，如 {@code 0.158}、{@code 南北通透}
 * @param inputs   计算用到的原始输入快照（窗面积、地面面积…），用于明细页"数据来源字段"列（FR-52）
 * @param reason   数据不足时的原因（缺哪个字段），供管理员补录（FR-57）
 */
public record MetricResult(String code, Double value, boolean ok, String display,
                           Map<String, String> inputs, String reason) {

    public static MetricResult of(String code, double value, String display, Map<String, String> inputs) {
        return new MetricResult(code, value, true, display, inputs, null);
    }

    public static MetricResult bool(String code, boolean value, String display, Map<String, String> inputs) {
        return new MetricResult(code, value ? 1d : 0d, true, display, inputs, null);
    }

    public static MetricResult insufficient(String code, String reason) {
        return new MetricResult(code, null, false, "—", Map.of(), reason);
    }
}
