package com.rallytrack.backend.domain.analysis.service;

import com.rallytrack.backend.domain.analysis.dto.AnalysisCompleteRequest;
import com.rallytrack.backend.domain.analysis.dto.AnalysisReportResponse;
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
                        .build())
                .collect(Collectors.toList());

        return AnalysisReportResponse.builder()
                .videoId(videoId)
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
                        .build());
            }
        }

        // 타임라인 이벤트 생성 (hits_data 기반)
        if (request.getHitsData() != null) {
            for (AnalysisCompleteRequest.HitData hitData : request.getHitsData()) {
                int ts = hitData.getTimeSec() != null ? hitData.getTimeSec().intValue() : 0;
                String displayTime = String.format("%d:%02d", ts / 60, ts % 60);
                String playerLabel = "pink_top".equals(hitData.getPlayer()) ? "상단(핑크)" : "하단(라임)";

                timelineEventRepository.save(TimelineEvent.builder()
                        .video(video)
                        .timestamp(ts)
                        .displayTime(displayTime)
                        .eventType(EventType.HIT)
                        .eventTitle("#" + hitData.getHitNumber() + " " + playerLabel)
                        .eventDescription(hitData.getPlayer())
                        .hitNumber(hitData.getHitNumber())
                        .player(hitData.getPlayer())
                        .build());
            }
        }

        // Video 업데이트
        video.setVideoStatus("COMPLETED");
        video.setDurationSeconds(deriveDurationSeconds(request.getHitsData()));
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
}
