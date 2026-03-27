package com.rallytrack.backend.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 서버 → 백엔드 분석 완료 콜백 DTO
 *
 * AI 서버 analysis_router.py의 callback_data와 필드가 일치해야 합니다.
 * hitsData 항목은 snake_case로 전송되므로 @JsonProperty로 명시합니다.
 */
@Getter
@NoArgsConstructor
public class AnalysisCompleteRequest {

    private Long videoId;
    private Float videoFps;
    private Integer totalHits;
    private List<HitData> hitsData;
    private String skeletonVideoUrl;
    private String minimapVideoUrl;

    @Getter
    @NoArgsConstructor
    public static class HitData {

        @JsonProperty("hit_number")
        private Integer hitNumber;

        private Integer frame;

        // AI 서버에서 소수점 초 단위로 전송 (예: 1.500)
        @JsonProperty("time_sec")
        private Float timeSec;

        // "pink_top" | "green_bottom"
        private String player;
    }
}
