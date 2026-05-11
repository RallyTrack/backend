package com.rallytrack.backend.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AnalysisReportResponse {

    private Long videoId;
    private SummaryDto summary;
    private PlayersDto players;

    // 추후 삭제할 legacy, 프론트단에서 flat field참조 안하게 되면 삭제
    private PositionAnalysisDto positionAnalysis;
    private StrokeTypesDto strokeTypes;
    private AbilityMetricsDto abilityMetrics;
    private AiCoachingDto aiCoaching;

    private Float videoFps;
    private Integer totalHits;
    private List<HitDto> hitsData;

    // 코트 상단(pink_top) / 하단(green_bottom) 기준 점수
    private Integer topPlayerScore;
    private Integer bottomPlayerScore;

    // "TOP_WIN" | "BOTTOM_WIN" | "DRAW"
    private String matchOutcome;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class HitDto {
        private Integer hitNumber;
        private Integer frame;
        private Float timeSec;
        private String player;
        private String strokeType;
    }
}
