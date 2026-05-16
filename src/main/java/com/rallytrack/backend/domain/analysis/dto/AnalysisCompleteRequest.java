package com.rallytrack.backend.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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

    @JsonProperty("rally_results")
    private List<RallyResultData> rallyResults;

    @JsonProperty("player_metrics")
    private Map<String, PlayerMetricsData> playerMetrics;

    @Getter
    @NoArgsConstructor
    public static class RallyResultData {

        @JsonProperty("rally_idx")
        private Integer rallyIdx;

        @JsonProperty("last_hit_number")
        private Integer lastHitNumber;

        @JsonProperty("last_hit_owner")
        private String lastHitOwner;

        @JsonProperty("result_type")
        private String resultType;

        @JsonProperty("is_in")
        private Boolean isIn;

        private String location;
    }

    @Getter
    @NoArgsConstructor
    public static class PlayerMetricsData {

        @JsonProperty("home_return_rate")
        private Integer homeReturnRate;
    }

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

        // 스트로크 분류 결과 (없으면 null) — "Serve"|"Defense"|"Lob"|"Smash"|"Drop"|"Drive"|"Net"|"Clear"|"Push"
        @JsonProperty("stroke_type")
        private String strokeType;

        @JsonProperty("stroke_confidence")
        private Float strokeConfidence;

        // 타격 시점 선수 중심 좌표 (0.0~1.0 정규화, AI 서버 미제공 시 null)
        @JsonProperty("player_x")
        private Float playerX;

        @JsonProperty("player_y")
        private Float playerY;
    }
}
