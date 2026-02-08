package com.rallytrack.backend.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AnalysisReportResponse {

    private Long videoId;
    private Summary summary;
    private PositionAnalysis positionAnalysis;
    @JsonRawValue
    private String strokeTypes;
    @JsonRawValue
    private String abilityMetrics;
    private AiCoaching aiCoaching;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary {
        private Integer myScore;
        private Integer opponentScore;
        private String matchOutcome;
        private Integer totalStrokeCount;
        private String matchTime;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class PositionAnalysis {
        @JsonRawValue
        private String heatmapData;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class AiCoaching {
        private String feedbackText;
    }
}
