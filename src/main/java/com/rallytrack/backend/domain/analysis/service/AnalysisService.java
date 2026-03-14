package com.rallytrack.backend.domain.analysis.service;

import com.rallytrack.backend.domain.analysis.dto.AnalysisCompleteRequest;
import com.rallytrack.backend.domain.analysis.dto.AnalysisReportResponse;
import com.rallytrack.backend.domain.analysis.entity.AnalysisResult;
import com.rallytrack.backend.domain.analysis.repository.AnalysisResultRepository;
import com.rallytrack.backend.domain.video.entity.TimelineEvent;
import com.rallytrack.backend.domain.video.entity.Video;
import com.rallytrack.backend.domain.video.repository.TimelineEventRepository;
import com.rallytrack.backend.domain.video.repository.VideoRepository;

import com.rallytrack.backend.global.exception.ResourceNotFroundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisResultRepository analysisResultRepository;
    private final VideoRepository videoRepository;
    private final TimelineEventRepository timelineEventRepository;

    @Transactional(readOnly = true)
    public AnalysisReportResponse getReport(Long videoId) {
        AnalysisResult result = analysisResultRepository.findByVideoVideoId(videoId)
                .orElseThrow(() -> new ResourceNotFroundException("해당 영상의 분석 결과가 없습니다."));

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

    @Transactional
    public void saveResult(AnalysisCompleteRequest request) {
        // videoId로 영상 찾기
        Video video = videoRepository.findById(request.getVideoId())
                .orElseThrow(() -> new IllegalArgumentException("영상을 찾을 수 없습니다."));

        // 분석 결과 저장
        AnalysisResult result = AnalysisResult.builder()
                .video(video)
                .myScore(request.getMyScore())
                .opponentScore(request.getOpponentScore())
                .matchOutcome(request.getMatchOutcome())
                .totalStrokeCount(request.getTotalStrokeCount())
                .matchTime(request.getMatchTime())
                .heatmapData(request.getHeatmapData())
                .strokeTypes(request.getStrokeTypes())
                .abilityMetrics(request.getAbilityMetrics())
                .aiFeedback(request.getAiFeedback())
                .build();

        analysisResultRepository.save(result);  // analysis_results에 INSERT

        // 타임라인 이벤트 저장
        if (request.getTimelineEvents() != null) {
            for (AnalysisCompleteRequest.TimelineEventRequest eventReq : request.getTimelineEvents()) {
                TimelineEvent event = TimelineEvent.builder()
                        .video(video)
                        .timestamp(eventReq.getTimestamp())
                        .displayTime(eventReq.getDisplayTime())
                        .eventType(eventReq.getEventType())
                        .eventTitle(eventReq.getEventTitle())
                        .eventDescription(eventReq.getEventDescription())
                        .eventScore(eventReq.getEventScore())
                        .build();

                timelineEventRepository.save(event);
            }
        }

        // 영상 정보 업데이트
        video.setMatchScore(request.getMyScore() + ":" + request.getOpponentScore());
        video.setVideoStatus("COMPLETED");
        video.setSkeletonVideoUrl(request.getSkeletonVideoUrl());
        video.setDuration(request.getMatchTime());


        videoRepository.save(video);
    }


}
