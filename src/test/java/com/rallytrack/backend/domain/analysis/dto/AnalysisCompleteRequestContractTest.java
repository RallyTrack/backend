package com.rallytrack.backend.domain.analysis.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisCompleteRequestContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsAnalysisModeAndActualClassifierSchemesFromAiCallback() throws Exception {
        AnalysisCompleteRequest request = objectMapper.readValue("""
                {
                  "videoId": 10,
                  "analysisMode": "pro",
                  "strokeClassSchemes": ["9class"],
                  "totalHits": 6
                }
                """, AnalysisCompleteRequest.class);

        assertThat(request.getAnalysisMode()).isEqualTo("pro");
        assertThat(request.getStrokeClassSchemes()).containsExactly("9class");
    }
}
