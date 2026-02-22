package com.rallytrack.backend.domain.analysis.controller;

import com.rallytrack.backend.domain.analysis.dto.AnalysisCompleteRequest;
import com.rallytrack.backend.domain.analysis.dto.AnalysisReportResponse;
import com.rallytrack.backend.domain.analysis.service.AnalysisService;
import com.rallytrack.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Analysis", description = "분석 리포트 API")
@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
public class AnalysisController {


    private final AnalysisService analysisService;

    @Operation(summary = "리포트 조회", description = "영상 분석 리포트를 조회합니다.")
    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<AnalysisReportResponse>> getReport(
            @PathVariable Long videoId) {

        AnalysisReportResponse response = analysisService.getReport(videoId);
        return ResponseEntity.ok(ApiResponse.success("분석 리포트 조회 성공", response));
    }

    public ResponseEntity<ApiResponse<Void>> analysisComplete(@RequestBody AnalysisCompleteRequest request) {
        analysisService.saveResult(request);
        return ResponseEntity.ok(ApiResponse.success("분석 결과 저장 완료", null));
    }

}