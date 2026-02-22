package com.rallytrack.backend.domain.analysis.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AnalysisCompleteRequest {  // 분석서버가 보내는 json의 모든 필드를 받는 DTO

    private Long videoId;
    private Integer myScore;
    private Integer opponentScore;
    private String matchOutcome;
    private Integer totalStrokeCount;
    private String matchTime;
    private String heatmapData;
    private String strokeTypes;
    private String abilityMetrics;
    private String aiFeedback;
    private List<TimelineEventRequest> timelineEvents;

    @Getter
    @NoArgsConstructor
    public static class TimelineEventRequest {
        private Integer timestamp;
        private String displayTime;
        private String eventType;
        private String eventTitle;
        private String eventDescription;
        private Integer eventScore;

    }

}
