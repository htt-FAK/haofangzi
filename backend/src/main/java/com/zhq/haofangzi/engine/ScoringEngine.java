package com.zhq.haofangzi.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 户型评估引擎（M4 内核；spec 004 plan §1，docs/04 §4 的 Java 落点）。
 *
 * <p>职责边界：只做<b>确定性计算</b>，不查库、不写库、不调用大模型（可复现 AC-32 的前提）。
 * 数据装载与快照落库由 {@code EvaluationService} 负责。
 *
 * <p>线程安全：{@link RuleSetView} 与 {@link MetricContext} 不可变，{@link MetricCalculator} 无状态，
 * 因此可被 Web 线程并发共享调用（NFR-01：单次 p95 ≤ 200ms，21 指标串行 < 5ms 计算量）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringEngine {

    private final RuleRepository ruleRepository;
    private final MetricCalculator.Registry registry;
    private final ScoreAssembler assembler;

    public ScoreAssembler.Outcome score(MetricContext ctx) {
        return score(ctx, ruleRepository.active(), Map.of());
    }

    /**
     * @param set            规则集视图（ACTIVE 或 DRAFT 试算版本）
     * @param weightOverride 人群模板覆写的维度权重（FR-53）；null/空表示按发布权重
     */
    public ScoreAssembler.Outcome score(MetricContext ctx, RuleSetView set, Map<String, Double> weightOverride) {
        Map<String, MetricResult> results = new LinkedHashMap<>();

        // 1) 只计算被规则引用到的指标（省算力，也让"未注册指标"可被单独告警）
        set.dims().forEach(dim -> dim.rules().forEach(rule -> {
            String code = rule.metricCode().toUpperCase();
            if (results.containsKey(code)) {
                return;
            }
            Optional<MetricCalculator> calc = registry.find(code);
            if (calc.isEmpty()) {
                log.warn("规则引用了未注册的指标 {}（按数据不足处理，不阻塞评分）", code);
                results.put(code, MetricResult.insufficient(code, "指标计算器未注册"));
                return;
            }
            results.put(code, safeCalc(calc.get(), ctx, code));
        }));

        // 2) 分档 + 归一 + 汇总（全在 ScoreAssembler，纯函数）
        ScoreAssembler.Outcome outcome = assembler.assemble(set, results, weightOverride);

        // 3) 自检：分数越界是设计错误，宁可告警也不静默（宪法：正确性优先；FR-57 也禁止 NaN）
        if (Double.isNaN(outcome.total()) || outcome.total() < 0 || outcome.total() > 100) {
            log.error("评估结果越界 total={} houseType={}，按有效维度检查归一逻辑", outcome.total(),
                    ctx.ht() == null ? null : ctx.ht().getCode());
        }
        return outcome;
    }

    private MetricResult safeCalc(MetricCalculator calc, MetricContext ctx, String code) {
        try {
            MetricResult r = calc.calc(ctx);
            return r == null ? MetricResult.insufficient(code, "计算器返回空") : r;
        } catch (RuntimeException e) {
            log.warn("指标 {} 计算异常，降级为数据不足", code, e);
            return MetricResult.insufficient(code, "计算异常：" + e.getClass().getSimpleName());
        }
    }

    /** 人群模板（FR-53）：只覆写维度权重，范围由 {@link ScoreAssembler} 二次夹取（防 AI/前端越界） */
    public enum CrowdTemplate {
        GENERAL, FAMILY_3, FAMILY_4, MULTI_GEN, SENIOR_COUPLE, INVESTOR;

        /** 内置模板权重（发布前由管理员校对；模板之和必须归一为 1） */
        public Map<String, Double> weights() {
            Map<String, Double> m = new LinkedHashMap<>();
            switch (this) {
                case FAMILY_3 -> {
                    m.put("LIGHT", 0.20); m.put("VENT", 0.20); m.put("CIRC", 0.18);
                    m.put("UTIL", 0.18); m.put("QUIET", 0.10); m.put("GREEN", 0.06); m.put("COST", 0.08);
                }
                case MULTI_GEN -> {      // 三代同堂：实用/静谧优先（对应 AC-33）
                    m.put("LIGHT", 0.16); m.put("VENT", 0.22); m.put("CIRC", 0.16);
                    m.put("UTIL", 0.20); m.put("QUIET", 0.14); m.put("GREEN", 0.06); m.put("COST", 0.06);
                }
                case SENIOR_COUPLE -> {
                    m.put("LIGHT", 0.22); m.put("VENT", 0.18); m.put("CIRC", 0.14);
                    m.put("UTIL", 0.18); m.put("QUIET", 0.16); m.put("GREEN", 0.08); m.put("COST", 0.04);
                }
                case INVESTOR -> {       // 投资客：经济权重上提
                    m.put("LIGHT", 0.14); m.put("VENT", 0.14); m.put("CIRC", 0.12);
                    m.put("UTIL", 0.16); m.put("QUIET", 0.10); m.put("GREEN", 0.06); m.put("COST", 0.28);
                }
                default -> m.clear();   // GENERAL/FAMILY_4：沿用发布权重
            }
            return m;
        }

        public static CrowdTemplate of(String code) {
            if (code == null || code.isBlank()) {
                return GENERAL;
            }
            return valueOf(code.toUpperCase());
        }
    }
}
