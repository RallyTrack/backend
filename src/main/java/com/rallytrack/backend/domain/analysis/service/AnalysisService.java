package com.rallytrack.backend.domain.analysis.service;

import com.rallytrack.backend.domain.analysis.dto.*;
import com.rallytrack.backend.domain.analysis.entity.AnalysisResult;
import com.rallytrack.backend.domain.analysis.entity.Hit;
import com.rallytrack.backend.domain.analysis.repository.AnalysisResultRepository;
import com.rallytrack.backend.domain.analysis.repository.HitRepository;
import com.rallytrack.backend.domain.video.entity.EventType;
import com.rallytrack.backend.domain.video.entity.TimelineEvent;
import com.rallytrack.backend.domain.video.entity.Video;
import com.rallytrack.backend.domain.video.repository.TimelineEventRepository;
import com.rallytrack.backend.domain.video.repository.VideoRepository;
import com.rallytrack.backend.global.exception.ResourceNotFroundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisResultRepository analysisResultRepository;
    private final HitRepository hitRepository;
    private final VideoRepository videoRepository;
    private final TimelineEventRepository timelineEventRepository;

    // ── 분석 리포트 조회 ─────────────────────────────────────

    @Transactional(readOnly = true)
    public AnalysisReportResponse getReport(Long videoId) {
        AnalysisResult result = analysisResultRepository.findByVideoVideoId(videoId)
                .orElseThrow(() -> new ResourceNotFroundException("해당 영상의 분석 결과가 없습니다."));

        List<AnalysisReportResponse.HitDto> hitDtos = result.getHits().stream()
                .map(h -> AnalysisReportResponse.HitDto.builder()
                        .hitNumber(h.getHitNumber())
                        .frame(h.getFrame())
                        .timeSec(h.getTimeSec())
                        .player(h.getPlayer())
                        .strokeType(h.getStrokeType())
                        .build())
                .collect(Collectors.toList());

        List<Hit> hits = result.getHits();

        int topHitCount = (int) hits.stream()
                .filter(h -> "top".equals(normalizePlayer(h.getPlayer())))
                .count();

        int bottomHitCount = (int) hits.stream()
                .filter(h -> "bottom".equals(normalizePlayer(h.getPlayer())))
                .count();

        PlayerReportDto topReport = buildPlayerReport(topHitCount, hits, "top");
        PlayerReportDto bottomReport = buildPlayerReport(bottomHitCount, hits, "bottom");

        PlayersDto players = PlayersDto.builder()
                .top(topReport)
                .bottom(bottomReport)
                .build();

        SummaryDto summary = SummaryDto.builder()
                .myScore(result.getBottomPlayerScore() != null ? result.getBottomPlayerScore() : 0)
                .opponentScore(result.getTopPlayerScore() != null ? result.getTopPlayerScore() : 0)
                .matchOutcome(toFrontendOutcome(result.getTopPlayerScore(), result.getBottomPlayerScore()))
                .totalStrokeCount(result.getTotalHits() != null ? result.getTotalHits() : hits.size())
                .matchTime(formatDurationSeconds(result.getVideo().getDurationSeconds()))
                .build();

        return AnalysisReportResponse.builder()
                .videoId(videoId)
                .summary(summary)
                .players(players)
                // legacy flat fields: 일단 bottom 기준으로 mirror
                .positionAnalysis(bottomReport.getPositionAnalysis())
                .strokeTypes(bottomReport.getStrokeTypes())
                .abilityMetrics(bottomReport.getAbilityMetrics())
                .aiCoaching(bottomReport.getAiCoaching())
                // 기존 debug/호환 필드
                .videoFps(result.getVideoFps())
                .totalHits(result.getTotalHits())
                .hitsData(hitDtos)
                .topPlayerScore(result.getTopPlayerScore())
                .bottomPlayerScore(result.getBottomPlayerScore())
                .matchOutcome(result.getMatchOutcome())
                .build();
    }

    // ── AI 서버 콜백 저장 ────────────────────────────────────

    @Transactional
    public void saveResult(AnalysisCompleteRequest request) {
        Video video = videoRepository.findById(request.getVideoId())
                .orElseThrow(() -> new IllegalArgumentException("영상을 찾을 수 없습니다."));

        // hits_data 기반 점수 파생 계산
        // TODO: 배드민턴 규칙에 맞게 정교화 필요 (현재는 랠리 마지막 타격자 기준 단순 추정)
        ScoreResult score = deriveScore(request.getHitsData());

        // AnalysisResult 저장
        AnalysisResult analysisResult = AnalysisResult.builder()
                .video(video)
                .videoFps(request.getVideoFps())
                .totalHits(request.getTotalHits())
                .topPlayerScore(score.topPlayerScore)
                .bottomPlayerScore(score.bottomPlayerScore)
                .matchOutcome(score.matchOutcome)
                .build();

        analysisResultRepository.save(analysisResult);

        // 개별 타점(Hit) 저장
        if (request.getHitsData() != null) {
            for (AnalysisCompleteRequest.HitData hitData : request.getHitsData()) {
                hitRepository.save(Hit.builder()
                        .analysisResult(analysisResult)
                        .hitNumber(hitData.getHitNumber())
                        .frame(hitData.getFrame())
                        .timeSec(hitData.getTimeSec())
                        .player(hitData.getPlayer())
                        .strokeType(hitData.getStrokeType())
                        .playerX(hitData.getPlayerX())
                        .playerY(hitData.getPlayerY())
                        .build());
            }
        }

        // 타임라인 이벤트 생성 (hits_data 기반)
        if (request.getHitsData() != null) {
            for (AnalysisCompleteRequest.HitData hitData : request.getHitsData()) {
                int ts = hitData.getTimeSec() != null ? hitData.getTimeSec().intValue() : 0;
                String displayTime = String.format("%d:%02d", ts / 60, ts % 60);
                String playerLabel = "pink_top".equals(hitData.getPlayer()) ? "상단(핑크)" : "하단(라임)";
                String strokeLabel = hitData.getStrokeType() != null ? " [" + hitData.getStrokeType() + "]" : "";

                timelineEventRepository.save(TimelineEvent.builder()
                        .video(video)
                        .timestamp(ts)
                        .displayTime(displayTime)
                        .eventType(EventType.HIT)
                        .eventTitle("#" + hitData.getHitNumber() + " " + playerLabel + strokeLabel)
                        .eventDescription(hitData.getPlayer())
                        .hitNumber(hitData.getHitNumber())
                        .player(hitData.getPlayer())
                        .build());
            }
        }

        // Video 업데이트
        video.setVideoStatus("COMPLETED");
        if (video.getDurationSeconds() == null || video.getDurationSeconds() <= 0) {
            video.setDurationSeconds(deriveDurationSeconds(request.getHitsData()));
        }
        video.setMatchScore(score.topPlayerScore + ":" + score.bottomPlayerScore);
        if (request.getSkeletonVideoUrl() != null) {
            video.setSkeletonVideoUrl(request.getSkeletonVideoUrl());
        }
        if (request.getMinimapVideoUrl() != null) {
            video.setMinimapVideoUrl(request.getMinimapVideoUrl());
        }
        videoRepository.save(video);
    }

    // ── 점수 파생 계산 ────────────────────────────────────────

    /**
     * hits_data의 랠리 구조에서 코트 상단/하단 점수를 추정합니다.
     *
     * 배드민턴 기본 규칙: 마지막으로 타격한 선수가 실점 (상대 득점).
     * 랠리 구분: 타점 간격이 3초 이상 벌어지면 새 랠리로 분리.
     *
     * 예) A→B→A 순서로 타격 후 A가 마지막 → B 득점 (topPlayer 기준)
     *
     * TODO: 실제 배드민턴 규칙(서브권, 코트 in/out 판정 등)에 맞게 정교화 필요
     */
    private ScoreResult deriveScore(List<AnalysisCompleteRequest.HitData> hitsData) {
        if (hitsData == null || hitsData.isEmpty()) {
            return new ScoreResult(0, 0, "DRAW");
        }

        int topScore    = 0; // pink_top 득점
        int bottomScore = 0; // green_bottom 득점

        AnalysisCompleteRequest.HitData lastInRally = hitsData.get(0);
        AnalysisCompleteRequest.HitData prev        = hitsData.get(0);

        for (int i = 1; i < hitsData.size(); i++) {
            AnalysisCompleteRequest.HitData hit = hitsData.get(i);

            boolean isNewRally = hit.getTimeSec() != null
                    && prev.getTimeSec() != null
                    && (hit.getTimeSec() - prev.getTimeSec()) > 3.0f;

            if (isNewRally) {
                // 이전 랠리 종료: 마지막 타격자가 실점 → 상대 득점
                if ("pink_top".equals(lastInRally.getPlayer())) {
                    bottomScore++;
                } else {
                    topScore++;
                }
            }

            lastInRally = hit;
            prev = hit;
        }

        // 마지막 랠리 처리
        if ("pink_top".equals(lastInRally.getPlayer())) {
            bottomScore++;
        } else {
            topScore++;
        }

        topScore    = Math.min(topScore, 21);
        bottomScore = Math.min(bottomScore, 21);

        String outcome = topScore > bottomScore ? "TOP_WIN"
                       : topScore < bottomScore ? "BOTTOM_WIN"
                       : "DRAW";

        return new ScoreResult(topScore, bottomScore, outcome);
    }

    /**
     * 마지막 타점 시간 + 10초를 영상 길이(초)로 추정합니다.
     */
    private Integer deriveDurationSeconds(List<AnalysisCompleteRequest.HitData> hitsData) {
        if (hitsData == null || hitsData.isEmpty()) return 0;
        AnalysisCompleteRequest.HitData last = hitsData.get(hitsData.size() - 1);
        return last.getTimeSec() != null ? last.getTimeSec().intValue() + 10 : 0;
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────

    private static class ScoreResult {
        final int topPlayerScore;
        final int bottomPlayerScore;
        final String matchOutcome;

        ScoreResult(int topPlayerScore, int bottomPlayerScore, String matchOutcome) {
            this.topPlayerScore    = topPlayerScore;
            this.bottomPlayerScore = bottomPlayerScore;
            this.matchOutcome      = matchOutcome;
        }
    }

    private PlayerReportDto buildPlayerReport(int hitCount, List<Hit> allHits, String playerSide) {
        List<Hit> playerHits = allHits.stream()
                .filter(h -> playerSide.equals(normalizePlayer(h.getPlayer())))
                .collect(Collectors.toList());

        long smash  = playerHits.stream().filter(h -> "Smash".equals(h.getStrokeType())).count();
        long clear  = playerHits.stream().filter(h -> "Clear".equals(h.getStrokeType())).count();
        long drop   = playerHits.stream().filter(h -> "Drop".equals(h.getStrokeType())).count();
        long drive  = playerHits.stream().filter(h -> "Drive".equals(h.getStrokeType())).count();
        long serve  = playerHits.stream().filter(h -> "Serve".equals(h.getStrokeType())).count();
        long net    = playerHits.stream().filter(h -> "Net".equals(h.getStrokeType())).count();
        long others = playerHits.stream().filter(h -> {
            String s = h.getStrokeType();
            return s == null || !List.of("Smash", "Clear", "Drop", "Drive", "Serve", "Net").contains(s);
        }).count();

        List<Hit> allHitsSorted = allHits.stream()
                .filter(h -> h.getTimeSec() != null)
                .sorted(Comparator.comparing(Hit::getTimeSec))
                .collect(Collectors.toList());

        AbilityMetricsDto abilityMetrics = calculateAbilityMetrics(allHitsSorted, playerHits, playerSide);

        return PlayerReportDto.builder()
                .positionAnalysis(PositionAnalysisDto.builder()
                        .heatmapData(List.of())
                        .build())
                .strokeTypes(StrokeTypesDto.builder()
                        .smash((int) smash)
                        .clear((int) clear)
                        .drop((int) drop)
                        .drive((int) drive)
                        .serve((int) serve)
                        .net((int) net)
                        .others((int) others)
                        .build())
                .abilityMetrics(abilityMetrics)
                .aiCoaching(AiCoachingDto.builder()
                        .feedbackText("")
                        .build())
                .build();
    }

    private String normalizePlayer(String player) {
        if (player == null) return "bottom";

        if ("top".equals(player) || "pink_top".equals(player)) {
            return "top";
        }

        if ("bottom".equals(player) || "green_bottom".equals(player)) {
            return "bottom";
        }

        return "bottom";
    }

    private String toFrontendOutcome(Integer topPlayerScore, Integer bottomPlayerScore) {
        int top = topPlayerScore != null ? topPlayerScore : 0;
        int bottom = bottomPlayerScore != null ? bottomPlayerScore : 0;

        if (bottom > top) return "WIN";
        if (bottom < top) return "LOSE";
        return "DRAW";
    }

    private String formatDurationSeconds(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) {
            return "0:00";
        }
        return String.format("%d:%02d", durationSeconds / 60, durationSeconds % 60);
    }

    private String formatMatchTime(List<Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "0:00";
        }

        int seconds = hits.stream()
                .map(Hit::getTimeSec)
                .filter(t -> t != null)
                .mapToInt(Float::intValue)
                .max()
                .orElse(0);

        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Hit 데이터(timeSec, player, strokeType, playerX/Y)로 5개 능력치(0~100)를 산출.
     *
     * ① 스매시      : smash 타격 비율 × 2.5          (40% rate → 100)
     * ② 평균 랠리 시간 : 참여 랠리 평균 지속초 / 30s × 100   (30초 → 100)
     * ③ 속도        : (10 - 평균타격간격초) / 8 × 100    (2초 간격 → 100)
     * ④ 이동 거리   : playerX/Y 있으면 실측 이동량, 없으면 랠리 참여율로 근사
     * ⑤ 실책률      : 랠리 마지막 타격자 비율            (낮을수록 실책 적음)
     */
    private AbilityMetricsDto calculateAbilityMetrics(
            List<Hit> allHitsSorted,
            List<Hit> playerHits,
            String playerSide
    ) {
        if (playerHits.isEmpty() || allHitsSorted.isEmpty()) {
            return AbilityMetricsDto.builder()
                    .smash(0).avgRallyTime(0).speed(0).distance(0).errorRate(0)
                    .build();
        }

        int totalPlayerHits = playerHits.size();

        // 전체 경기 활성 시간 (최소 1초)
        float matchDurationSec =
                allHitsSorted.get(allHitsSorted.size() - 1).getTimeSec()
                - allHitsSorted.get(0).getTimeSec();
        if (matchDurationSec < 1f) matchDurationSec = 1f;

        // 랠리 분리 (3초 이상 공백 = 새 랠리)
        List<List<Hit>> rallies = splitIntoRallies(allHitsSorted, 3.0f);

        // ① 스매시 — smash 비율 × 2.5 (40% rate = 100점)
        long smashCount = playerHits.stream()
                .filter(h -> "Smash".equals(h.getStrokeType()))
                .count();
        int smashScore = clamp((int) Math.round(smashCount * 100.0 / totalPlayerHits * 2.5));

        // ② 평균 랠리 시간 — 이 플레이어가 참여한 랠리의 평균 지속 초 / 30s × 100
        double avgRallyDuration = rallies.stream()
                .filter(r -> r.size() >= 2 &&
                        r.stream().anyMatch(h -> playerSide.equals(normalizePlayer(h.getPlayer()))))
                .mapToDouble(r -> (double)(
                        r.get(r.size() - 1).getTimeSec() - r.get(0).getTimeSec()))
                .average()
                .orElse(0.0);
        int avgRallyTimeScore = clamp((int) Math.round(avgRallyDuration / 30.0 * 100));

        // ③ 속도 — 경기 시간 ÷ 타격 수 = 평균 타격 간격 / 2초→100, 10초→0
        double avgInterval = matchDurationSec / (double) totalPlayerHits;
        int speedScore = clamp((int) Math.round((10.0 - avgInterval) / 8.0 * 100));

        // ④ 이동 거리
        List<Hit> sortedPlayerHits = playerHits.stream()
                .filter(h -> h.getTimeSec() != null)
                .sorted(Comparator.comparing(Hit::getTimeSec))
                .collect(Collectors.toList());

        boolean hasPosData = sortedPlayerHits.stream()
                .anyMatch(h -> h.getPlayerX() != null && h.getPlayerY() != null);

        int distanceScore;
        if (hasPosData) {
            // 실측: 연속 타격 간 유클리드 거리 합산 (0~1 좌표계)
            // 기준값: 30타 × 평균이동 0.2 = 6.0 → 100점
            double totalDist = 0.0;
            Hit prev = null;
            for (Hit h : sortedPlayerHits) {
                if (h.getPlayerX() == null || h.getPlayerY() == null) { prev = null; continue; }
                if (prev != null && prev.getPlayerX() != null) {
                    double dx = h.getPlayerX() - prev.getPlayerX();
                    double dy = h.getPlayerY() - prev.getPlayerY();
                    totalDist += Math.sqrt(dx * dx + dy * dy);
                }
                prev = h;
            }
            distanceScore = clamp((int) Math.round(totalDist / 6.0 * 100));
        } else {
            // 위치 없음 → 랠리 참여율로 근사 (95% 참여 = 100점)
            long ralliesParticipated = rallies.stream()
                    .filter(r -> r.stream()
                            .anyMatch(h -> playerSide.equals(normalizePlayer(h.getPlayer()))))
                    .count();
            distanceScore = clamp((int) Math.round(
                    ralliesParticipated * 100.0 / Math.max(1, rallies.size()) * (100.0 / 95.0)));
        }

        // ⑤ 실책률 — 랠리 마지막 타격자 = 실점 가정
        long ralliesParticipated = rallies.stream()
                .filter(r -> r.stream()
                        .anyMatch(h -> playerSide.equals(normalizePlayer(h.getPlayer()))))
                .count();
        long errors = rallies.stream()
                .filter(r -> !r.isEmpty() &&
                        playerSide.equals(normalizePlayer(r.get(r.size() - 1).getPlayer())))
                .count();
        int errorRateScore = ralliesParticipated > 0
                ? clamp((int) Math.round(errors * 100.0 / ralliesParticipated))
                : 0;

        return AbilityMetricsDto.builder()
                .smash(smashScore)
                .avgRallyTime(avgRallyTimeScore)
                .speed(speedScore)
                .distance(distanceScore)
                .errorRate(errorRateScore)
                .build();
    }

    /**
     * 시간순 정렬된 Hit 목록을 랠리 단위로 분리.
     * 연속 두 타격 사이 간격이 gapSec 초 초과면 새 랠리.
     */
    private List<List<Hit>> splitIntoRallies(List<Hit> sortedHits, float gapSec) {
        List<List<Hit>> rallies = new ArrayList<>();
        if (sortedHits.isEmpty()) return rallies;

        List<Hit> current = new ArrayList<>();
        current.add(sortedHits.get(0));

        for (int i = 1; i < sortedHits.size(); i++) {
            float gap = sortedHits.get(i).getTimeSec() - sortedHits.get(i - 1).getTimeSec();
            if (gap > gapSec) {
                rallies.add(current);
                current = new ArrayList<>();
            }
            current.add(sortedHits.get(i));
        }
        rallies.add(current);
        return rallies;
    }

    /** 값을 0~100 범위로 클램프. */
    private static int clamp(int value) {
        return Math.min(100, Math.max(0, value));
    }
}
