package com.zhq.haofangzi.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 打分汇总器（spec 004 §1 打分管线第 2~5 步；docs/04 §4.3/§4.4 的 Java 落点）。
 * 纯函数、无 IO、可脱离 Spring 单测（引擎行覆盖率 ≥80% 的门禁主要落在这里）。
 *
 * <p>算法：
 * <pre>
 * 指标分  = tier.score（未命中/数据不足 → 该指标缺席）
 * 维度分  = Σ(指标分 × 维度内权重) ÷ Σ(有效指标内权重)      ← 缺失时的归一化，保证不因缺数据罚总分（FR-57/AC-31）
 * 维度贡献 = 维度分 × 维度有效权重（人群模板覆写后归一）
 * 总分    = Σ 维度贡献，保留 1 位小数；等级按 thresholds 左闭映射
 * 建议    = 命中"中/差"档的规则，按 提升潜力 = 维度权重 × 维度内权重 × (100 − 指标分) 降序取前 3
 * </pre>
 */
@Component
public class ScoreAssembler {

    private static final double FULL = 100d;

    private final TierMatcher tierMatcher;

    public ScoreAssembler(TierMatcher tierMatcher) {
        this.tierMatcher = tierMatcher;
    }

    public Outcome assemble(RuleSetView set, Map<String, MetricResult> metricResults,
                           Map<String, Double> weightOverride) {

        Map<String, Double> weights = normalizeWeights(set, weightOverride);
        List<DimScore> dimScores = new ArrayList<>();
        List<MetricScore> allMetrics = new ArrayList<>();
        int missing = 0;

        for (RuleSetView.DimView dim : set.dims()) {
            double w = weights.getOrDefault(dim.code(), 0d);
            double validWeight = 0d;
            double acc = 0d;
            List<MetricScore> ms = new ArrayList<>();

            for (RuleSetView.RuleView rule : dim.rules()) {
                MetricResult mr = metricResults.get(rule.metricCode().toUpperCase());
                if (mr == null || !mr.ok()) {
                    missing++;
                    ms.add(MetricScore.missing(rule, mr == null ? "指标未实现" : mr.reason()));
                    continue;
                }
                Optional<RuleSetView.Tier> tier = tierMatcher.match(mr.value(), rule.tiers());
                if (tier.isEmpty()) {
                    missing++;
                    ms.add(MetricScore.missing(rule, "指标值未落入任何分档（请检查规则边界）"));
                    continue;
                }
                RuleSetView.Tier t = tier.get();
                acc += t.score() * rule.internalWeight();
                validWeight += rule.internalWeight();
                ms.add(new MetricScore(rule.metricCode(), rule.metricName(), mr.display(), t.grade(), t.score(),
                        rule.internalWeight(), rule.basis(), evidence(rule, mr, t), false, rule.sourceFields()));
            }

            double dimScore = validWeight <= 0d ? 0d : BigDecimal.valueOf(acc / validWeight)
                    .setScale(1, RoundingMode.HALF_UP).doubleValue();
            boolean dimMissing = validWeight <= 0d;
            dimScores.add(new DimScore(dim.code(), dim.name(), w, dimMissing ? null : dimScore,
                    dimMissing ? null : round1(dimScore * w), ms));
            allMetrics.addAll(ms);
        }

        double total = dimScores.stream().filter(d -> d.contrib() != null).mapToDouble(DimScore::contrib).sum();
        total = round1(total);
        int finalMissing = missing;
        List<Suggestion> suggestions = buildSuggestions(set, allMetrics, weights);
        return new Outcome(round1(total), set.thresholds().levelOf(total), dimScores, allMetrics,
                finalMissing, suggestions);
    }

    /** 模板覆写：只改维度权重，不改指标计算（FR-53），覆写后重新归一保证 Σ=1 */
    private Map<String, Double> normalizeWeights(RuleSetView set, Map<String, Double> override) {
        Map<String, Double> base = new java.util.LinkedHashMap<>();
        set.dims().forEach(d -> base.put(d.code(), d.weight()));
        if (override != null) {
            override.forEach((k, v) -> {
                if (v != null && v >= 0.02d && v <= 0.30d && base.containsKey(k)) {
                    base.put(k, v);
                }
            });
        }
        double sum = base.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0d) {
            return base;
        }
        double finalSum = sum;
        base.replaceAll((k, v) -> round3(v / finalSum));
        return base;
    }

    private String evidence(RuleSetView.RuleView rule, MetricResult mr, RuleSetView.Tier t) {
        String tpl = t.evidence() == null ? "{code} = {value} 命中「{grade}」" : t.evidence();
        return tpl.replace("{code}", rule.metricName())
                .replace("{value}", mr.display() == null ? String.valueOf(mr.value()) : mr.display())
                .replace("{grade}", t.grade())
                .replace("{unit}", rule.unit() == null ? "" : rule.unit())
                + (mr.inputs() == null || mr.inputs().isEmpty() ? "" : "（输入：" + mr.inputs() + "）")
                + (rule.basis() == null ? "" : "；依据：" + rule.basis());
    }

    private List<Suggestion> buildSuggestions(RuleSetView set, List<MetricScore> metrics, Map<String, Double> weights) {
        List<Suggestion> out = new ArrayList<>();
        for (RuleSetView.DimView dim : set.dims()) {
            for (RuleSetView.RuleView rule : dim.rules()) {
                metrics.stream().filter(m -> m.metricCode().equals(rule.metricCode()))
                        .filter(m -> !m.missing() && ("中".equals(m.grade()) || "差".equals(m.grade())))
                        .forEach(m -> {
                            double potential = weights.getOrDefault(dim.code(), 0d) * rule.internalWeight() * (FULL - m.score());
                            String text = rule.suggestion() == null ? m.metricName() + " 偏弱，可现场复核"
                                    : rule.suggestion().replace("{value}", m.value()).replace("{need}", m.grade());
                            out.add(new Suggestion(dim.name(), m.metricName(), text, round1(potential)));
                        });
            }
        }
        out.sort(Comparator.comparingDouble(Suggestion::potential).reversed());
        return out.stream().distinct().limit(3).toList();
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    // ── 输出结构（同时是 detail_json 快照的序列化对象，FR-51 / AC-32）────────────────
    public record Outcome(double total, String level, List<DimScore> dimensions, List<MetricScore> metrics,
                          int missingCount, List<Suggestion> suggestions) {
    }

    public record DimScore(String code, String name, double weight, Double score, Double contrib,
                           List<MetricScore> metrics) {
    }

    public record MetricScore(String metricCode, String metricName, String value, String grade, double score,
                              double internalWeight, String basis, String evidence, boolean missing,
                              List<String> sourceFields) {

        static MetricScore missing(RuleSetView.RuleView rule, String reason) {
            return new MetricScore(rule.metricCode(), rule.metricName(), "—", "数据不足", 0d,
                    rule.internalWeight(), rule.basis(), "缺少输入：" + reason, true, rule.sourceFields());
        }
    }

    public record Suggestion(String dimension, String metric, String text, double potential) {
    }
}
