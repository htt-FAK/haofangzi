package com.zhq.haofangzi.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 进程内评分耗时。不启动服务，也不打 HTTP。 */
class EvaluationPerfTest {

    @Test
    @DisplayName("同一套评分重复跑，p95 低于 200 毫秒")
    void assembleP95Under200ms() {
        ScoreAssembler assembler = new ScoreAssembler(new TierMatcher());
        RuleSetView set = new RuleSetView(1, "性能", "v1", "GENERAL",
                List.of(new RuleSetView.DimView("LIGHT", "采光", 0.5, List.of(rule("LIGHT_wfa"))),
                        new RuleSetView.DimView("VENT", "通风", 0.5, List.of(rule("VENT_cross")))),
                RuleSetView.LevelThresholds.defaults());
        Map<String, MetricResult> metrics = Map.of(
                "LIGHT_WFA", MetricResult.of("LIGHT_wfa", 0.158, "0.158", Map.of()),
                "VENT_CROSS", MetricResult.bool("VENT_cross", true, "通透", Map.of()));
        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long t0 = System.nanoTime();
            assembler.assemble(set, metrics, Map.of());
            samples.add((System.nanoTime() - t0) / 1_000_000);
        }
        samples.sort(Long::compareTo);
        long p95 = samples.get((int) Math.floor(0.95 * (samples.size() - 1)));
        assertThat(p95).isLessThan(200);
    }

    private static RuleSetView.RuleView rule(String code) {
        return new RuleSetView.RuleView(code, code, "-", "BETWEEN", 1.0, true,
                List.of(new RuleSetView.Tier(0d, null, null, 80, "良", null)),
                "单测", null, List.of());
    }
}
