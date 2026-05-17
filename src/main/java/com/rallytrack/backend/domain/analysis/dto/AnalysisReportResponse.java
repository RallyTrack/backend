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

    // 추후 삭제할 legacy
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

        // 히트맵 좌표 (0~100, 선수 위치 기반 호모그래피 변환)
        @JsonProperty("minimap_x")
        private Float minimapX;

        @JsonProperty("minimap_y")
        private Float minimapY;

        // 선수 중심 좌표 (0~1 프레임 정규화)
        @JsonProperty("player_x")
        private Float playerX;

        @JsonProperty("player_y")
        private Float playerY;
    }
}