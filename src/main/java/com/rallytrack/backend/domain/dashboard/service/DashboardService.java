package com.rallytrack.backend.domain.dashboard.service;

import com.rallytrack.backend.config.S3Service;
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
    private final S3Service s3Service;  // ✅ 추가: 썸네일 presigned URL 생성을 위한 S3Service

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long userId) {
        List<Video> videos = videoRepository
                .findByUserIdAndVideoStatusNotOrderByUploadDateDesc(userId, "DELETED");

        // 총 분석 시간 (초) 집계 — durationSeconds(Integer)로 단순 합산
        int totalSeconds = videos.stream()
                .filter(v -> v.getDurationSeconds() != null)
                .mapToInt(Video::getDurationSeconds)
                .sum();

        String totalTime = String.format("%d시간 %d분",
                totalSeconds / 3600, (totalSeconds % 3600) / 60);

        // 평균 점수 (top 플레이어 기준)
        int avgScore = 0;
        if (!videos.isEmpty()) {
            int totalTopScore = 0;
            int count = 0;
            for (Video v : videos) {
                Optional<AnalysisResult> ar = analysisResultRepository.findByVideoVideoId(v.getVideoId());
                if (ar.isPresent() && ar.get().getTopPlayerScore() != null) {
                    totalTopScore += ar.get().getTopPlayerScore();
                    count++;
                }
            }
            if (count > 0) avgScore = totalTopScore / count;
        }

        // 최근 영상 리스트 (최대 10개)
        List<DashboardResponse.RecentVideo> recentVideos = videos.stream()
                .limit(10)
                .map(v -> {
                    // 화면 표시용 시간 문자열 변환 — 초 → "M:SS"
                    String playTime = formatDuration(v.getDurationSeconds());
                    
                    // ✅ 수정: thumbnailUrl을 presigned URL로 변환
                    String thumbnailUrl = v.getThumbnailUrl() != null 
                        ? s3Service.generatePresignedUrl(v.getThumbnailUrl())
                        : null;

                    return DashboardResponse.RecentVideo.builder()
                            .videoId(v.getVideoId())
                            .title(v.getTitle())
                            .date(v.getMatchDate() != null
                                    ? v.getMatchDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    : v.getUploadDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                            .playTime(playTime)
                            .matchScore(v.getMatchScore())
                            .thumbnailUrl(thumbnailUrl)  // ✅ presigned URL 사용
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

    // ── 헬퍼 ─────────────────────────────────────────────────

    /**
     * 초 단위 정수를 "M:SS" 형태 문자열로 변환합니다.
     * null이거나 0 이하면 "0:00" 반환.
     */
    private String formatDuration(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) return "0:00";
        return String.format("%d:%02d", durationSeconds / 60, durationSeconds % 60);
    }
}