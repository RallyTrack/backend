package com.rallytrack.backend.domain.analysis.controller;

import com.rallytrack.backend.domain.analysis.dto.AnalysisCompleteRequest;
import com.rallytrack.backend.domain.analysis.dto.AnalysisReportResponse;
import com.rallytrack.backend.domain.analysis.service.AnalysisService;
import com.rallytrack.backend.domain.video.entity.Video;
import com.rallytrack.backend.domain.video.repository.VideoRepository;
import com.rallytrack.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Analysis", description = "분석 리포트 API")
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final VideoRepository videoRepository;

    @Operation(summary = "분석 리포트 조회", description = "영상 분석 리포트를 조회합니다.")
    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<AnalysisReportResponse>> getReport(
            @PathVariable Long videoId) {
        AnalysisReportResponse response = analysisService.getReport(videoId);
        return ResponseEntity.ok(ApiResponse.success("분석 리포트 조회 성공", response));
    }

    @Operation(summary = "분석 완료 콜백", description = "AI 서버에서 분석 완료 시 호출합니다.")
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<Void>> analysisComplete(
            @RequestBody AnalysisCompleteRequest request) {
        analysisService.saveResult(request);
        return ResponseEntity.ok(ApiResponse.success("분석 결과 저장 완료", null));
    }

    @Operation(summary = "분석 실패 콜백", description = "AI 서버에서 분석 실패 시 호출합니다.")
    @PostMapping("/fail")
    public ResponseEntity<ApiResponse<Void>> analysisFail(
            @RequestBody Map<String, Object> body) {
        Long videoId = Long.valueOf(body.get("videoId").toString());
        String error = body.getOrDefault("error", "알 수 없는 오류").toString();

        videoRepository.findById(videoId).ifPresent(video -> {
            video.setVideoStatus("FAILED");
            videoRepository.save(video);
        });

        System.err.println("[분석 실패] videoId=" + videoId + ", error=" + error);
        return ResponseEntity.ok(ApiResponse.success("실패 상태 업데이트 완료", null));
    }
}
