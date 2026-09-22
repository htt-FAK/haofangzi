package com.zhq.haofangzi.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultRulesJsonTest {

    @Test
    @DisplayName("默认规则：7 维权重和为 1，登记指标全部在文件里，维度内权重和为 1")
    void weightsAndCoverage() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/rules/default-rules.json")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = new ObjectMapper().readValue(in, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dims = (List<Map<String, Object>>) raw.get("dimensions");
            double dimSum = 0;
            int metrics = 0;
            for (Map<String, Object> dim : dims) {
                dimSum += ((Number) dim.get("weight")).doubleValue();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rules = (List<Map<String, Object>>) dim.get("rules");
                double inner = 0;
                for (Map<String, Object> rule : rules) {
                    inner += ((Number) rule.get("internalWeight")).doubleValue();
                    metrics++;
                }
                assertThat(inner).isCloseTo(1d, org.assertj.core.data.Offset.offset(0.001));
            }
            assertThat(dimSum).isCloseTo(1d, org.assertj.core.data.Offset.offset(0.001));
            assertThat(metrics).isEqualTo(25);
            assertThat(dims).hasSize(7);
        }
    }
}
