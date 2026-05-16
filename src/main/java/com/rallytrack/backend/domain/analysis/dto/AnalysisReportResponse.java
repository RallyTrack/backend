package com.rallytrack.backend.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // 추후 삭제할 legacy (프론트에서 flat field 참조 안 하게 되면 삭제)
    private PositionAnalysisDto positionAnalysis;
    private StrokeTypesDto strokeTypes;
    private AbilityMetricsDto abilityMetrics;
    private AiCoachingDto aiCoaching;

    private Float videoFps;
    private Integer totalHits;
    private List<HitDto> hitsData;

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

        // 미니맵과 동일한 셔틀콕 좌표 (0~100) — 프론트 히트맵에서 사용
        // snake_case로 직렬화해야 reportpageApi.ts의 minimap_x/y 필드명과 일치
        @JsonProperty("minimap_x")
        private Float minimapX;

        @JsonProperty("minimap_y")
        private Float minimapY;
    }
}