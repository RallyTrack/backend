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
        private String thumbnailUrl;
        private Integer duration;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class MatchSummary {
        private String matchScore;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class TimelineEventDto {
        private Long eventId;
        private Integer timestamp;
        private String displayTime;
        private String type;
        private String title;
        private String description;
    }
}
