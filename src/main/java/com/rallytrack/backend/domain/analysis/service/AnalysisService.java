package com.rallytrack.backend.domain.analysis.service;

import com.rallytrack.backend.domain.analysis.dto.AnalysisReportResponse;
import com.rallytrack.backend.domain.analysis.entity.AnalysisResult;
import com.rallytrack.backend.domain.analysis.repository.AnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisResultRepository analysisResultRepository;

    @Transactional(readOnly = true)
    public AnalysisReportResponse getReport(Long videoId) {
        AnalysisResult result = analysisResultRepository.findByVideoVideoId(videoId)
                .orElseThrow(() -> new IllegalArgumentException("분석 결과를 찾을 수 없습니다."));

        return AnalysisReportResponse.builder()
                .videoId(videoId)
                .summary(AnalysisReportResponse.Summary.builder()
                        .myScore(result.getMyScore())
                        .opponentScore(result.getOpponentScore())
                        .matchOutcome(result.getMatchOutcome())
                        .totalStrokeCount(result.getTotalStrokeCount())
                        .matchTime(result.getMatchTime())
                        .build())
                .positionAnalysis(AnalysisReportResponse.PositionAnalysis.builder()
                        .heatmapData(result.getHeatmapData())
                        .build())
                .strokeTypes(result.getStrokeTypes())
                .abilityMetrics(result.getAbilityMetrics())
                .aiCoaching(AnalysisReportResponse.AiCoaching.builder()
                        .feedbackText(result.getAiFeedback())
                        .build())
                .build();
    }
}
