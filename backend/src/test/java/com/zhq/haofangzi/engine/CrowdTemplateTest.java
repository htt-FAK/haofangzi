package com.zhq.haofangzi.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CrowdTemplateTest {

    @Test
    void blankUsesGeneralAndUnknownIsRejected() {
        assertThat(ScoringEngine.CrowdTemplate.of(null)).isEqualTo(ScoringEngine.CrowdTemplate.GENERAL);
        assertThat(ScoringEngine.CrowdTemplate.of("  ")).isEqualTo(ScoringEngine.CrowdTemplate.GENERAL);
        assertThatThrownBy(() -> ScoringEngine.CrowdTemplate.of("NOT_A_TEMPLATE"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
