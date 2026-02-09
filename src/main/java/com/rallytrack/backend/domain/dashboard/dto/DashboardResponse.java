package com.rallytrack.backend.domain.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class DashboardResponse {

    private DashboardSummary dashboardSummary;
    private List<RecentVideo> recentVideos;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class DashboardSummary {
        private int totalVideos;
        private String totalAnalysisTime;
        private int averageScore;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class RecentVideo {
        private Long videoId;
        private String title;
        private String date;
        private String playTime;
        private String matchScore;
        private String thumbnailUrl;
        private Actions actions;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class Actions {
        private String viewVideoUrl;
        private String viewAnalysisUrl;
    }
}
