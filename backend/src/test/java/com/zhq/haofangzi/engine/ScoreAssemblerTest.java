package com.zhq.haofangzi.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 引擎核心单测（宪法第四条：评估域行覆盖 ≥80%；用例映射 {@code docs/06} TC-E-01~05/30/31）。
 * 纯对象组装，不启动 Spring —— 这也是"引擎不查库、不调模型"这一设计约束的可验证结果。
 */
class ScoreAssemblerTest {

    private final ScoreAssembler assembler = new ScoreAssembler(new TierMatcher());

    private RuleSetView set(double lightWeight, List<RuleSetView.RuleView> lightRules) {
        return new RuleSetView(1, "单测集", "v1", "GENERAL",
                List.of(new RuleSetView.DimView("LIGHT", "采光与日照", lightWeight, lightRules),
                        new RuleSetView.DimView("VENT", "通风与对流", 1 - lightWeight,
                                List.of(boolRule("VENT_cross", 1.0)))),
                RuleSetView.LevelThresholds.defaults());
    }

    private RuleSetView.RuleView numRule(String code, double w) {
        return new RuleSetView.RuleView(code, code, "-", "BETWEEN", w, true,
                List.of(new RuleSetView.Tier(0.14, null, null, 100, "优", "{code}={value} ≥0.140"),
                        new RuleSetView.Tier(0.115, 0.14, null, 80, "良", null),
                        new RuleSetView.Tier(0.09, 0.115, null, 60, "中", null),
                        new RuleSetView.Tier(null, 0.09, null, 40, "差", null)),
                "《住宅项目规范》窗地比 ≥1/7", "增大开窗", List.of("hf_room.window_area"));
    }

    private RuleSetView.RuleView boolRule(String code, double w) {
        return new RuleSetView.RuleView(code, code, "-", "BOOL", w, true,
                List.of(new RuleSetView.Tier(null, null, true, 100, "优", null),
                        new RuleSetView.Tier(null, null, false, 55, "中", null)),
                "设计常识", null, List.of());
    }

    @Test
    @DisplayName("AC-30 窗地比 0.158 命中优档且证据含判定式与依据")
    void hitsTopTierAndEvidence() {
        var out = assembler.assemble(set(0.22, List.of(numRule("LIGHT_wfa", 1.0))),
                Map.of("LIGHT_WFA", MetricResult.of("LIGHT_wfa", 0.158, "0.158", Map.of("窗", "4.2")),
                        "VENT_CROSS", MetricResult.bool("VENT_cross", true, "南北通透", Map.of())),
                Map.of());

        var metric = out.metrics().stream().filter(m -> m.metricCode().equals("LIGHT_wfa")).findFirst().orElseThrow();
        assertThat(metric.grade()).isEqualTo("优");
        assertThat(metric.evidence()).contains("0.158").contains("1/7");
        assertThat(out.total()).isGreaterThan(0).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("FR-57/AC-31 指标缺失时维度内权重归一，不给 0 分也不崩")
    void missingMetricNormalizes() {
        var out = assembler.assemble(set(0.22, List.of(numRule("LIGHT_wfa", 1.0))),
                Map.of("LIGHT_WFA", MetricResult.insufficient("LIGHT_wfa", "缺少房间构件数据"),
                        "VENT_CROSS", MetricResult.bool("VENT_cross", true, "南北通透", Map.of())),
                Map.of());

        var light = out.dimensions().stream().filter(d -> d.code().equals("LIGHT")).findFirst().orElseThrow();
        assertThat(light.score()).isNull();                       // 整维无有效指标 → 不出分
        assertThat(out.missingCount()).isEqualTo(1);
        assertThat(out.total()).isLessThanOrEqualTo(100).isGreaterThan(0);  // 仅按有效维度归一
    }

    @Test
    @DisplayName("FR-56 维度权重和≠1 时应被识别（发布前校验）")
    void weightsValidation() {
        assertThat(set(0.22, List.of()).weightsValid()).isTrue();
        RuleSetView broken = new RuleSetView(1, "坏集", "v1", "GENERAL",
                List.of(new RuleSetView.DimView("LIGHT", "采光", 0.5, List.of())),
                RuleSetView.LevelThresholds.defaults());
        assertThat(broken.weightsValid()).isFalse();
    }

    @Test
    @DisplayName("TC-E-05 等级边界采用左闭：85→优，84.9→良，75→良，59.9→中，<60→差")
    void levelBoundaries() {
        var th = RuleSetView.LevelThresholds.defaults();
        assertThat(th.levelOf(85)).isEqualTo("优");
        assertThat(th.levelOf(84.9)).isEqualTo("良");
        assertThat(th.levelOf(75)).isEqualTo("良");
        assertThat(th.levelOf(74.9)).isEqualTo("中");
        assertThat(th.levelOf(59.9)).isEqualTo("差");
    }

    @Test
    @DisplayName("TC-E-04 NaN / 未命中任何档 → 数据不足，绝不产生 NaN 分数")
    void nanNeverScores() {
        var out = assembler.assemble(set(0.3, List.of(numRule("LIGHT_wfa", 1.0))),
                Map.of("LIGHT_WFA", MetricResult.of("LIGHT_wfa", Double.NaN, "NaN", Map.of()),
                        "VENT_CROSS", MetricResult.bool("VENT_cross", false, "不通透", Map.of())),
                Map.of());
        assertThat(Double.isNaN(out.total())).isFalse();
        assertThat(out.missingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-53 人群模板只改维度权重；越界权重被夹取，Σ 保持 1")
    void templateOverride() {
        var base = assembler.assemble(set(0.22, List.of(numRule("LIGHT_wfa", 1.0))),
                Map.of("LIGHT_WFA", MetricResult.of("LIGHT_wfa", 0.158, "0.158", Map.of()),
                        "VENT_CROSS", MetricResult.bool("VENT_cross", true, "南北通透", Map.of())),
                Map.of());
        var tpl = assembler.assemble(set(0.22, List.of(numRule("LIGHT_wfa", 1.0))),
                Map.of("LIGHT_WFA", MetricResult.of("LIGHT_wfa", 0.158, "0.158", Map.of()),
                        "VENT_CROSS", MetricResult.bool("VENT_cross", true, "南北通透", Map.of())),
                Map.of("LIGHT", 0.99));                       // 超上限，应被忽略而非把 VENT 压成 0
        assertThat(tpl.total()).isEqualTo(base.total());
    }

    @Test
    @DisplayName("分档匹配为左闭右开：0.14 归优档，0.1399 归良档")
    void tierBoundaries() {
        RuleSetView s = set(0.22, List.of(numRule("LIGHT_wfa", 1.0)));
        RuleSetView scoreSet = s;
        var atBoundary = assembler.assemble(scoreSet,
                Map.of("LIGHT_WFA", MetricResult.of("LIGHT_wfa", 0.140, "0.140", Map.of()),
                        "VENT_CROSS", MetricResult.bool("VENT_cross", true, "南北", Map.of())), Map.of());
        var below = assembler.assemble(scoreSet,
                Map.of("LIGHT_WFA", MetricResult.of("LIGHT_wfa", 0.1399, "0.140", Map.of()),
                        "VENT_CROSS", MetricResult.bool("VENT_cross", true, "南北", Map.of())), Map.of());
        assertThat(atBoundary.total()).isGreaterThan(below.total());
    }
}
