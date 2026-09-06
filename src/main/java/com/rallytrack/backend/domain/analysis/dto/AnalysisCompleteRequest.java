package com.rallytrack.backend.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * AI 서버 → 백엔드 분석 완료 콜백 DTO
 *
 * AI 서버 pipeline_service.py의 callback_data와 필드가 일치해야 합니다.
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
    private String analysisMode;
    private List<String> strokeClassSchemes;

    @JsonProperty("rallyResults")
    private List<RallyResultData> rallyResults;

    @JsonProperty("player_metrics")
    private Map<String, PlayerMetricsData> playerMetrics;

    @Getter
    @NoArgsConstructor
    public static class RallyResultData {

        @JsonProperty("rallyIdx")
        private Integer rallyIdx;

        @JsonProperty("lastHitNumber")
        private Integer lastHitNumber;

        @JsonProperty("lastHitOwner")
        private String lastHitOwner;

        @JsonProperty("resultType")
        private String resultType;

        @JsonProperty("isIn")
        private Boolean isIn;

        private String location;

        @JsonProperty("marginCm")
        private Float marginCm;

        private String confidence;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PlayerMetricsData {

        @JsonProperty("home_return_rate")
        private Float homeReturnRate;
    }

    @Getter
    @NoArgsConstructor
    public static class HitData {

        @JsonProperty("hit_number")
        private Integer hitNumber;

        private Integer frame;

        @JsonProperty("time_sec")
        private Float timeSec;

        // "top" | "bottom"
        private String player;

        @JsonProperty("stroke_type")
        private String strokeType;

        @JsonProperty("stroke_confidence")
        private Float strokeConfidence;

        // 타격 시점 선수 중심 좌표 (0.0~1.0 정규화, AI 서버 미제공 시 null)
        @JsonProperty("player_x")
        private Float playerX;

        @JsonProperty("player_y")
        private Float playerY;

        // 타격 시점 셔틀콕의 미니맵 좌표 (0~100, 히트맵 SVG 좌표계와 동일)
        // AI 서버 pipeline_service.py Step 7.9에서 frame_to_minimap() 후 주입
        @JsonProperty("minimap_x")
        private Float minimapX;

        @JsonProperty("minimap_y")
        private Float minimapY;
    }
}
