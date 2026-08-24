package com.rallytrack.backend.domain.dashboard.controller;

import com.rallytrack.backend.domain.dashboard.dto.DashboardResponse;
import com.rallytrack.backend.domain.dashboard.service.DashboardService;
import com.rallytrack.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "대시보드 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
// @CrossOrigin(origins = "http://localhost:5173") // WebConfig에서 전역으로 설정해놓음. 충돌 가능성 있기에 삭제
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "대시보드 조회", description = "경기 리스트 및 요약 통계를 조회합니다.")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        DashboardResponse response = dashboardService.getDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success("대시보드 데이터 조회 성공", response));
    }

    @Operation(summary = "활동 통계", description = "최근 7일 사용/업로드 추이를 조회합니다.")
    @GetMapping("/dashboard/activity")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getActivityStats(
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(ApiResponse.success("활동 통계 조회 성공",
                dashboardService.getActivityStats(userId)));
    }

    @Operation(summary = "퍼포먼스 트렌드", description = "최근 7주 분석 기반 퍼포먼스 추이를 조회합니다.")
    @GetMapping("/dashboard/trend")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getPerformanceTrend(
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");
        return ResponseEntity.ok(ApiResponse.success("퍼포먼스 트렌드 조회 성공",
                dashboardService.getPerformanceTrend(userId)));
    }
}
