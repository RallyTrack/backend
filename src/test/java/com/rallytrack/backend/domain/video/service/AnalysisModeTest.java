package com.rallytrack.backend.domain.video.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisModeTest {

    @Test
    void normalizesAmateurIgnoringCaseAndWhitespace() {
        assertThat(AnalysisMode.normalize("  AmAtEuR  ")).isEqualTo("amateur");
    }

    @Test
    void keepsProAsTheSafeDefault() {
        assertThat(AnalysisMode.normalize("pro")).isEqualTo("pro");
        assertThat(AnalysisMode.normalize(null)).isEqualTo("pro");
        assertThat(AnalysisMode.normalize("unknown")).isEqualTo("pro");
    }
}
