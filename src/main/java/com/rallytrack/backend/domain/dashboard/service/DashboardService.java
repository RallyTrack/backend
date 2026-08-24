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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.rallytrack.backend.domain.analysis.entity.Hit;

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

    // ── 활동 통계 (최근 7일) ──────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getActivityStats(Long userId) {
        List<Video> videos = videoRepository
                .findByUserIdAndVideoStatusNotOrderByUploadDateDesc(userId, "DELETED");
        List<AnalysisResult> results = fetchResults(videos);

        LocalDate today = LocalDate.now();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("M/d");

        List<Map<String, Object>> stats = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long uploadCount = videos.stream()
                    .filter(v -> v.getUploadDate() != null
                            && day.equals(v.getUploadDate().toLocalDate()))
                    .count();
            // 사용 횟수 근사치: 해당 날짜에 완료된 분석 수
            long usageCount = results.stream()
                    .filter(ar -> ar.getCreatedAt() != null
                            && day.equals(ar.getCreatedAt().toLocalDate()))
                    .count();

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("day", day.format(dayFmt));
            point.put("usageCount", usageCount);
            point.put("uploadCount", uploadCount);
            stats.add(point);
        }
        return Map.of("stats", stats);
    }

    // ── 퍼포먼스 트렌드 (최근 7주) ────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getPerformanceTrend(Long userId) {
        List<Video> videos = videoRepository
                .findByUserIdAndVideoStatusNotOrderByUploadDateDesc(userId, "DELETED");
        List<AnalysisResult> results = fetchResults(videos);

        LocalDate baseWeekStart = LocalDate.now().minusWeeks(6).with(DayOfWeek.MONDAY);

        List<Integer> smash = new ArrayList<>();
        List<Integer> defense = new ArrayList<>();
        List<Integer> accuracy = new ArrayList<>();

        for (int w = 0; w < 7; w++) {
            LocalDate weekStart = baseWeekStart.plusWeeks(w);
            LocalDate weekEnd = weekStart.plusWeeks(1);

            List<AnalysisResult> weekly = results.stream()
                    .filter(ar -> ar.getCreatedAt() != null)
                    .filter(ar -> {
                        LocalDate d = ar.getCreatedAt().toLocalDate();
                        return !d.isBefore(weekStart) && d.isBefore(weekEnd);
                    })
                    .collect(Collectors.toList());

            List<Hit> hits = weekly.stream()
                    .flatMap(ar -> ar.getHits().stream())
                    .collect(Collectors.toList());

            long total = hits.size();
            long smashCnt = hits.stream().filter(h -> "Smash".equals(h.getStrokeType())).count();
            // 수비성 스트로크: Clear(수비적 클리어) + Net(네트 처리)
            long defenseCnt = hits.stream()
                    .filter(h -> "Clear".equals(h.getStrokeType()) || "Net".equals(h.getStrokeType()))
                    .count();

            smash.add(total == 0 ? 0 : (int) Math.round(smashCnt * 100.0 / total));
            defense.add(total == 0 ? 0 : (int) Math.round(defenseCnt * 100.0 / total));
            accuracy.add(averageReturnRate(weekly));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("smash", smash);
        data.put("defense", defense);
        data.put("accuracy", accuracy);
        return data;
    }

    private List<AnalysisResult> fetchResults(List<Video> videos) {
        return videos.stream()
                .map(v -> analysisResultRepository.findByVideoVideoId(v.getVideoId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    // 리턴 성공률 평균 (top/bottom 평균). 0~1 스케일이면 %로 환산
    private int averageReturnRate(List<AnalysisResult> weekly) {
        List<Double> rates = new ArrayList<>();
        for (AnalysisResult ar : weekly) {
            if (ar.getHomeReturnRateTop() != null) rates.add(ar.getHomeReturnRateTop().doubleValue());
            if (ar.getHomeReturnRateBottom() != null) rates.add(ar.getHomeReturnRateBottom().doubleValue());
        }
        if (rates.isEmpty()) return 0;
        double avg = rates.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (avg <= 1.0) avg *= 100.0;
        return (int) Math.round(Math.min(avg, 100.0));
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