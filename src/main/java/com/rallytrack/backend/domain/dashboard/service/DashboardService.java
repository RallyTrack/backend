package com.rallytrack.backend.domain.dashboard.service;

import com.rallytrack.backend.domain.analysis.entity.AnalysisResult;
import com.rallytrack.backend.domain.analysis.repository.AnalysisResultRepository;
import com.rallytrack.backend.domain.dashboard.dto.DashboardResponse;
import com.rallytrack.backend.domain.video.entity.Video;
import com.rallytrack.backend.domain.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VideoRepository videoRepository;
    private final AnalysisResultRepository analysisResultRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId) {
        List<Video> videos = videoRepository.findByUserIdOrderByUploadDateDesc(userId);

        // 총 분석 시간 계산
        int totalSeconds = videos.stream()
                .map(Video::getDuration)
                .filter(d -> d != null)
                .mapToInt(Integer::intValue)
                .sum();
        String totalTime = String.format("%d시간 %d분",
                totalSeconds / 3600, (totalSeconds % 3600) / 60);

        // 평균 점수 계산
        int avgScore = 0;
        if (!videos.isEmpty()) {
            int totalMyScore = 0;
            int count = 0;
            for (Video v : videos) {
                Optional<AnalysisResult> ar = analysisResultRepository.findByVideoVideoId(v.getVideoId());
                if (ar.isPresent() && ar.get().getMyScore() != null) {
                    totalMyScore += ar.get().getMyScore();
                    count++;
                }
            }
            if (count > 0) avgScore = totalMyScore / count;
        }

        // 최근 영상 리스트
        List<DashboardResponse.RecentVideo> recentVideos = videos.stream()
                .limit(10)
                .map(v -> {
                    String playTime = v.getDuration() != null
                            ? String.format("%d:%02d", v.getDuration() / 60, v.getDuration() % 60)
                            : "0:00";

                    return DashboardResponse.RecentVideo.builder()
                            .videoId(v.getVideoId())
                            .title(v.getTitle())
                            .date(v.getMatchDate() != null
                                    ? v.getMatchDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    : v.getUploadDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                            .playTime(playTime)
                            .matchScore(v.getMatchScore())
                            .thumbnailUrl(v.getThumbnailUrl())
                            .actions(DashboardResponse.Actions.builder()
                                    .viewVideoUrl("/api/v1/videos/" + v.getVideoId())
                                    .viewAnalysisUrl("/api/v1/analysis/" + v.getVideoId())
                                    .build())
                            .build();
                })
                .collect(Collectors.toList());

        return DashboardResponse.builder()
                .dashboardSummary(DashboardResponse.DashboardSummary.builder()
                        .totalVideos(videos.size())
                        .totalAnalysisTime(totalTime)
                        .averageScore(avgScore)
                        .build())
                .recentVideos(recentVideos)
                .build();
    }
}
