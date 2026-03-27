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
    }
}
