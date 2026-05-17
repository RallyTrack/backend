package com.rallytrack.backend.domain.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class VideoDetailResponse {

    private VideoInfo videoInfo;
    private MatchSummary matchSummary;
    private List<TimelineEventDto> timelineEvents;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class VideoInfo {
        private Long videoId;
        private String title;
        private String videoUrl;
        private String skeletonVideoUrl;
        private String minimapVideoUrl;
        private String thumbnailUrl;
        // 초 단위 정수 — 프론트에서 "M:SS" 포맷 변환 또는 표시 용도
        private Integer durationSeconds;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MatchSummary {
        private String matchScore;
        private Integer unknownRallies;
        private Integer totalRallies;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class TimelineEventDto {
        private Long eventId;
        private Float timestamp;
        private String displayTime;
        private String type;       // EventType enum의 name() 값
        private String title;
        private String description;
        private Integer hitNumber; // HIT 이벤트일 때만 값 존재
        private String player;     // "pink_top" | "green_bottom"
    }
}
