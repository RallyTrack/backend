package com.rallytrack.backend.domain.dashboard.controller;

import com.rallytrack.backend.domain.dashboard.dto.DashboardResponse;
import com.rallytrack.backend.domain.dashboard.service.DashboardService;
import com.rallytrack.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "대시보드 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "대시보드 조회", description = "경기 리스트 및 요약 통계를 조회합니다.")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {

        DashboardResponse response = dashboardService.getDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success("대시보드 데이터 조회 성공", response));
    }
}
