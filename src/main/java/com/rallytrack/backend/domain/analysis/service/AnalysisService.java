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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        // HitDto에 minimap_x/y 포함 → 프론트 히트맵이 미니맵과 동일한 좌표 사용
        List<AnalysisReportResponse.HitDto> hitDtos = result.getHits().stream()
                .map(h -> AnalysisReportResponse.HitDto.builder()
                        .hitNumber(h.getHitNumber())
                        .frame(h.getFrame())
                        .timeSec(h.getTimeSec())
                        .player(h.getPlayer())
                        .strokeType(h.getStrokeType())
                        .minimapX(h.getMinimapX())
                        .minimapY(h.getMinimapY())
                        .playerX(h.getPlayerX())
                        .playerY(h.getPlayerY())
                        .build())
                .collect(Collectors.toList());

        List<Hit> hits = result.getHits();

        int topHitCount = (int) hits.stream()
                .filter(h -> "top".equals(normalizePlayer(h.getPlayer())))
                .count();

        int bottomHitCount = (int) hits.stream()
                .filter(h -> "bottom".equals(normalizePlayer(h.getPlayer())))
                .count();

        Map<String, AnalysisCompleteRequest.PlayerMetricsData> persistedMetrics = buildPersistedMetrics(result);

        PlayerReportDto topReport = buildPlayerReport(topHitCount, hits, "top", null, persistedMetrics);
        PlayerReportDto bottomReport = buildPlayerReport(bottomHitCount, hits, "bottom", null, persistedMetrics);

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
                // legacy flat fields
                .positionAnalysis(bottomReport.getPositionAnalysis())
                .strokeTypes(bottomReport.getStrokeTypes())
                .abilityMetrics(bottomReport.getAbilityMetrics())
                .aiCoaching(bottomReport.getAiCoaching())
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

        ScoreResult score = deriveScore(request.getHitsData(), request.getRallyResults());

        Float homeReturnRateTop = null;
        Float homeReturnRateBottom = null;
        if (request.getPlayerMetrics() != null) {
            AnalysisCompleteRequest.PlayerMetricsData pmTop = request.getPlayerMetrics().get("top");
            if (pmTop == null) pmTop = request.getPlayerMetrics().get("pink_top");
            if (pmTop != null) homeReturnRateTop = pmTop.getHomeReturnRate();

            AnalysisCompleteRequest.PlayerMetricsData pmBottom = request.getPlayerMetrics().get("bottom");
            if (pmBottom == null) pmBottom = request.getPlayerMetrics().get("green_bottom");
            if (pmBottom != null) homeReturnRateBottom = pmBottom.getHomeReturnRate();
        }
        System.out.println("[기동력] homeReturnRateTop=" + homeReturnRateTop + ", homeReturnRateBottom=" + homeReturnRateBottom);

        AnalysisResult analysisResult = AnalysisResult.builder()
                .video(video)
                .videoFps(request.getVideoFps())
                .totalHits(request.getTotalHits())
                .homeReturnRateTop(homeReturnRateTop)
                .homeReturnRateBottom(homeReturnRateBottom)
                .topPlayerScore(score.topPlayerScore)
                .bottomPlayerScore(score.bottomPlayerScore)
                .matchOutcome(score.matchOutcome)
                .unknownRallies(score.unknownRallies)
                .totalRallies(score.totalRallies)
                .build();

        analysisResultRepository.save(analysisResult);

        // 개별 타점(Hit) 저장 — minimap_x/y 포함
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
                        .minimapX(hitData.getMinimapX())
                        .minimapY(hitData.getMinimapY())
                        .build());
            }
        }

        // 타임라인 이벤트 생성
        if (request.getHitsData() != null) {
            for (AnalysisCompleteRequest.HitData hitData : request.getHitsData()) {
                int ts = hitData.getTimeSec() != null ? hitData.getTimeSec().intValue() : 0;
                String displayTime = String.format("%d:%02d", ts / 60, ts % 60);
                String strokeLabel = hitData.getStrokeType() != null ? hitData.getStrokeType() : "Unknown";

                timelineEventRepository.save(TimelineEvent.builder()
                        .video(video)
                        .timestamp(ts)
                        .displayTime(displayTime)
                        .eventType(EventType.HIT)
                        .eventTitle("#" + hitData.getHitNumber() + " " + strokeLabel)
                        .eventDescription(hitData.getPlayer())
                        .hitNumber(hitData.getHitNumber())
                        .player(hitData.getPlayer())
                        .build());
            }
        }

        video.setVideoStatus("COMPLETED");
        if (video.getDurationSeconds() == null || video.getDurationSeconds() <= 0) {
            video.setDurationSeconds(deriveDurationSeconds(request.getHitsData()));
        }
        video.setMatchScore(score.topPlayerScore + ":" + score.bottomPlayerScore);
        if (request.getSkeletonVideoUrl() != null) video.setSkeletonVideoUrl(request.getSkeletonVideoUrl());
        if (request.getMinimapVideoUrl() != null)  video.setMinimapVideoUrl(request.getMinimapVideoUrl());
        videoRepository.save(video);
    }

    // ── 점수 파생 계산 ────────────────────────────────────────

    /**
     * hits_data + rally_results 기반 점수 계산.
     *
     * WINNER          → 마지막 타격자 득점 (인바운드 확인)
     * CONFIRMED_ERROR → 상대 득점 (아웃 확인)
     * UNKNOWN / 없음  → 점수 미반영, unknownRallies 카운트
     */
    private ScoreResult deriveScore(
            List<AnalysisCompleteRequest.HitData> hitsData,
            List<AnalysisCompleteRequest.RallyResultData> rallyResults) {

        if (hitsData == null || hitsData.isEmpty()) {
            return new ScoreResult(0, 0, "DRAW", 0, 0);
        }

        Map<Integer, AnalysisCompleteRequest.RallyResultData> rrMap = new HashMap<>();
        if (rallyResults != null) {
            for (AnalysisCompleteRequest.RallyResultData rr : rallyResults) {
                if (rr.getLastHitNumber() != null) {
                    rrMap.put(rr.getLastHitNumber(), rr);
                }
            }
        }

        int topScore     = 0;
        int bottomScore  = 0;
        int unknownCount = 0;
        int totalRallies = 0;

        AnalysisCompleteRequest.HitData lastInRally = hitsData.get(0);
        AnalysisCompleteRequest.HitData prev        = hitsData.get(0);

        for (int i = 1; i < hitsData.size(); i++) {
            AnalysisCompleteRequest.HitData hit = hitsData.get(i);
            boolean isNewRally = hit.getTimeSec() != null
                    && prev.getTimeSec() != null
                    && (hit.getTimeSec() - prev.getTimeSec()) > 3.0f;

            if (isNewRally) {
                int[] res = applyRallyScore(lastInRally, rrMap, topScore, bottomScore, unknownCount);
                topScore     = res[0];
                bottomScore  = res[1];
                unknownCount = res[2];
                totalRallies++;
            }

            lastInRally = hit;
            prev        = hit;
        }

        int[] res = applyRallyScore(lastInRally, rrMap, topScore, bottomScore, unknownCount);
        topScore     = res[0];
        bottomScore  = res[1];
        unknownCount = res[2];
        totalRallies++;

        topScore    = Math.min(topScore, 30);
        bottomScore = Math.min(bottomScore, 30);

        String outcome = topScore > bottomScore ? "TOP_WIN"
                       : topScore < bottomScore ? "BOTTOM_WIN"
                       : "DRAW";

        System.out.printf("[점수계산] top=%d bottom=%d unknown=%d/%d%n",
                topScore, bottomScore, unknownCount, totalRallies);

        return new ScoreResult(topScore, bottomScore, outcome, unknownCount, totalRallies);
    }

    /**
     * 랠리 1개 득점 결정.
     *
     * WINNER          → 마지막 타격자 득점
     * CONFIRMED_ERROR → 상대 득점
     * UNKNOWN / null  → 점수 미반영
     *
     * @return [topScore, bottomScore, unknownCount]
     */
    private int[] applyRallyScore(
            AnalysisCompleteRequest.HitData lastHit,
            Map<Integer, AnalysisCompleteRequest.RallyResultData> rrMap,
            int topScore, int bottomScore, int unknownCount) {

        boolean isPinkTop = "pink_top".equals(lastHit.getPlayer());
        AnalysisCompleteRequest.RallyResultData rr =
                (lastHit.getHitNumber() != null) ? rrMap.get(lastHit.getHitNumber()) : null;

        String resultType = (rr != null) ? rr.getResultType() : null;

        if ("WINNER".equals(resultType)) {
            if (isPinkTop) topScore++;
            else           bottomScore++;
        } else if ("CONFIRMED_ERROR".equals(resultType)) {
            if (isPinkTop) bottomScore++;
            else           topScore++;
        } else {
            unknownCount++;
        }

        return new int[]{topScore, bottomScore, unknownCount};
    }

    private Integer deriveDurationSeconds(List<AnalysisCompleteRequest.HitData> hitsData) {
        if (hitsData == null || hitsData.isEmpty()) return 0;
        AnalysisCompleteRequest.HitData last = hitsData.get(hitsData.size() - 1);
        return last.getTimeSec() != null ? last.getTimeSec().intValue() + 10 : 0;
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────

    private static class ScoreResult {
        final int    topPlayerScore;
        final int    bottomPlayerScore;
        final String matchOutcome;
        final int unknownRallies;
        final int totalRallies;

        ScoreResult(int topPlayerScore, int bottomPlayerScore,
                    String matchOutcome, int unknownRallies, int totalRallies) {
            this.topPlayerScore    = topPlayerScore;
            this.bottomPlayerScore = bottomPlayerScore;
            this.matchOutcome      = matchOutcome;
            this.unknownRallies    = unknownRallies;
            this.totalRallies      = totalRallies;
        }
    }

    private PlayerReportDto buildPlayerReport(
            int hitCount, List<Hit> allHits, String playerSide,
            List<AnalysisCompleteRequest.RallyResultData> rallyResults,
            Map<String, AnalysisCompleteRequest.PlayerMetricsData> playerMetrics) {
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

        AbilityMetricsDto abilityMetrics = calculateAbilityMetrics(
                allHitsSorted,
                playerHits,
                playerSide,
                rallyResults,
                playerMetrics
        );

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
        if ("top".equals(player)    || "pink_top".equals(player))    return "top";
        if ("bottom".equals(player) || "green_bottom".equals(player)) return "bottom";
        return "bottom";
    }

    private String toFrontendOutcome(Integer topPlayerScore, Integer bottomPlayerScore) {
        int top    = topPlayerScore    != null ? topPlayerScore    : 0;
        int bottom = bottomPlayerScore != null ? bottomPlayerScore : 0;
        if (bottom > top) return "WIN";
        if (bottom < top) return "LOSE";
        return "DRAW";
    }

    private String formatDurationSeconds(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) return "0:00";
        return String.format("%d:%02d", durationSeconds / 60, durationSeconds % 60);
    }

    private AbilityMetricsDto calculateAbilityMetrics(
            List<Hit> allHitsSorted,
            List<Hit> playerHits,
            String playerSide,
            List<AnalysisCompleteRequest.RallyResultData> rallyResults,
            Map<String, AnalysisCompleteRequest.PlayerMetricsData> playerMetrics
    ) {
        if (playerHits.isEmpty() || allHitsSorted.isEmpty()) {
            return AbilityMetricsDto.builder()
                    .aggression(0).consistency(0).rally(0).mobility(0).defense(0)
                    .build();
        }

        List<List<Hit>> rallies = splitIntoRallies(allHitsSorted, 3.0f);
        int totalPlayerHits     = playerHits.size();

        // ── ① AGGRESSION ─────────────────────────────────────────
        long smashCount  = playerHits.stream().filter(h -> "Smash".equals(h.getStrokeType())).count();
        long driveCount  = playerHits.stream().filter(h -> "Drive".equals(h.getStrokeType())).count();
        long dropCount   = playerHits.stream().filter(h -> "Drop".equals(h.getStrokeType())).count();

        double strokeScore = (smashCount * 3.0 + driveCount * 2.0 + dropCount)
                / ((double) totalPlayerHits * 3.0) * 100.0;

        List<Float> pressureTimes = new ArrayList<>();
        for (int i = 0; i < allHitsSorted.size() - 1; i++) {
            Hit curr = allHitsSorted.get(i);
            Hit next = allHitsSorted.get(i + 1);
            if (playerSide.equals(normalizePlayer(curr.getPlayer()))
                    && !playerSide.equals(normalizePlayer(next.getPlayer()))
                    && curr.getTimeSec() != null && next.getTimeSec() != null) {
                pressureTimes.add(next.getTimeSec() - curr.getTimeSec());
            }
        }
        double fastShotRate = pressureTimes.isEmpty() ? 0.0
                : pressureTimes.stream().filter(t -> t < 1.2f).count()
                  / (double) pressureTimes.size() * 100.0;

        long winnerRallies = rallyResults == null ? 0L
                : rallyResults.stream()
                  .filter(r -> playerSide.equals(normalizePlayer(r.getLastHitOwner()))
                               && "WINNER".equals(r.getResultType()))
                  .count();
        long participatedRallies = rallies.stream()
                .filter(r -> r.stream().anyMatch(h -> playerSide.equals(normalizePlayer(h.getPlayer()))))
                .count();
        double winnerBonus = participatedRallies > 0
                ? (winnerRallies / (double) participatedRallies) * 30.0 : 0.0;

        double rawAggression = Math.max(strokeScore, fastShotRate) + winnerBonus;
        int aggressionScore = clamp((int) Math.round(rawAggression));

        // ── ② CONSISTENCY ────────────────────────────────────────
        int participatedCount = (int) participatedRallies;
        int myErrorCount     = 0;
        int notReceivedCount = 0;

        for (List<Hit> rally : rallies) {
            boolean playerInRally = rally.stream()
                    .anyMatch(h -> playerSide.equals(normalizePlayer(h.getPlayer())));
            if (!playerInRally) continue;

            Hit lastHit   = rally.get(rally.size() - 1);
            boolean isMyLast = playerSide.equals(normalizePlayer(lastHit.getPlayer()));

            if (isMyLast) {
                boolean isError = rallyResults != null && rallyResults.stream()
                        .filter(rr -> rr.getLastHitNumber() != null
                                   && rr.getLastHitNumber().equals(lastHit.getHitNumber()))
                        .findFirst()
                        .map(rr -> "CONFIRMED_ERROR".equals(rr.getResultType()))
                        .orElse(false);
                if (isError) myErrorCount++;
            } else {
                notReceivedCount++;
            }
        }

        double errorFreeRate = participatedCount > 0
                ? 1.0 - (myErrorCount / (double) participatedCount) : 1.0;
        double receptionRate = participatedCount > 0
                ? 1.0 - (notReceivedCount / (double) participatedCount) : 1.0;

        int consistencyScore = clamp(
                (int) Math.round((errorFreeRate * 0.5 + receptionRate * 0.5) * 100.0));

        // ── ③ RALLY SUSTAIN ──────────────────────────────────────
        List<Long> playerHitsPerRally = rallies.stream()
                .filter(r -> r.stream().anyMatch(h -> playerSide.equals(normalizePlayer(h.getPlayer()))))
                .map(r -> r.stream().filter(h -> playerSide.equals(normalizePlayer(h.getPlayer()))).count())
                .collect(Collectors.toList());

        double avgHitsPerRally = playerHitsPerRally.isEmpty() ? 0.0
                : playerHitsPerRally.stream().mapToLong(Long::longValue).average().orElse(0.0);
        int rallyScore = clamp((int) Math.round(avgHitsPerRally / 8.0 * 100.0));

        // ── ④ MOBILITY ───────────────────────────────────────────
        // AI 서버가 0.0~1.0 비율로 전송 → ×100 해서 0~100 점수로 변환
        int mobilityScore = 50;
        if (playerMetrics != null) {
            AnalysisCompleteRequest.PlayerMetricsData pm = playerMetrics.get(playerSide);
            if (pm != null && pm.getHomeReturnRate() != null) {
                mobilityScore = clamp(Math.round(pm.getHomeReturnRate() * 100));
            }
        }

        // ── ⑤ DEFENSE ────────────────────────────────────────────
        double defenseWeightedSum  = 0.0;
        double defenseWeightTotal  = 0.0;

        for (int i = 0; i < allHitsSorted.size(); i++) {
            Hit curr = allHitsSorted.get(i);
            if (!playerSide.equals(normalizePlayer(curr.getPlayer()))) continue;
            if (curr.getTimeSec() == null) continue;

            Hit prevOpponent = null;
            for (int j = i - 1; j >= 0; j--) {
                Hit candidate = allHitsSorted.get(j);
                if (!playerSide.equals(normalizePlayer(candidate.getPlayer()))
                        && candidate.getTimeSec() != null) {
                    prevOpponent = candidate;
                    break;
                }
            }
            if (prevOpponent == null) continue;

            float responseTime = curr.getTimeSec() - prevOpponent.getTimeSec();

            int difficultyWeight;
            if      (responseTime < 0.8f)  difficultyWeight = 3;
            else if (responseTime < 1.3f)  difficultyWeight = 2;
            else                           difficultyWeight = 1;

            boolean isLastHitInRally = (i == allHitsSorted.size() - 1)
                    || (allHitsSorted.get(i + 1).getTimeSec() - curr.getTimeSec() > 3.0f);

            int success;
            if (!isLastHitInRally) {
                success = 1;
            } else {
                success = 1;
                if (rallyResults != null) {
                    boolean isError = rallyResults.stream()
                            .filter(rr -> rr.getLastHitNumber() != null
                                       && rr.getLastHitNumber().equals(curr.getHitNumber()))
                            .findFirst()
                            .map(rr -> "CONFIRMED_ERROR".equals(rr.getResultType()))
                            .orElse(false);
                    success = isError ? 0 : 1;
                }
            }

            defenseWeightedSum += difficultyWeight * success;
            defenseWeightTotal += difficultyWeight;
        }

        int defenseScore = defenseWeightTotal > 0
                ? clamp((int) Math.round(defenseWeightedSum / defenseWeightTotal * 100.0))
                : 50;

        return AbilityMetricsDto.builder()
                .aggression(aggressionScore)
                .consistency(consistencyScore)
                .rally(rallyScore)
                .mobility(mobilityScore)
                .defense(defenseScore)
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

    private Map<String, AnalysisCompleteRequest.PlayerMetricsData> buildPersistedMetrics(AnalysisResult result) {
        Map<String, AnalysisCompleteRequest.PlayerMetricsData> map = new java.util.HashMap<>();
        if (result.getHomeReturnRateTop() != null) {
            AnalysisCompleteRequest.PlayerMetricsData pm = new AnalysisCompleteRequest.PlayerMetricsData();
            pm.setHomeReturnRate(result.getHomeReturnRateTop());
            map.put("top", pm);
        }
        if (result.getHomeReturnRateBottom() != null) {
            AnalysisCompleteRequest.PlayerMetricsData pm = new AnalysisCompleteRequest.PlayerMetricsData();
            pm.setHomeReturnRate(result.getHomeReturnRateBottom());
            map.put("bottom", pm);
        }
        return map.isEmpty() ? null : map;
    }

    /** 값을 0~100 범위로 클램프. */
    private static int clamp(int value) {
        return Math.min(100, Math.max(0, value));
    }
}
