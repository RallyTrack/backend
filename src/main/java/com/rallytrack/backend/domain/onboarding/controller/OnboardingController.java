package com.rallytrack.backend.domain.onboarding.controller;

import com.rallytrack.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Onboarding", description = "온보딩 API")
@RestController
@RequestMapping("/api/v1")
public class OnboardingController {

    @Operation(summary = "온보딩 정보 조회", description = "서비스 핵심 기능 소개 및 가입 유도 정보를 조회합니다.")
    @GetMapping("/onboarding")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOnboarding() {
        Map<String, Object> data = Map.of(
                "title", "AI 기반 배드민턴 경기 분석 플랫폼",
                "description", "영상을 업로드하면 AI가 자동으로 움직임을 분석하고 실력 향상을 위한 인사이트를 제공합니다.",
                "features", List.of(
                        Map.of("id", 1, "title", "영상 업로드",
                                "content", "경기 영상을 업로드하면 AI가 자동으로 선수와 셔틀콕을 인식하고 추적합니다."),
                        Map.of("id", 2, "title", "상세 분석",
                                "content", "히트맵, 이동 거리, 스트로크 분석 등 다양한 지표로 경기를 분석합니다."),
                        Map.of("id", 3, "title", "AI 코칭",
                                "content", "AI 코치가 당신의 플레이를 분석하고 개선점을 제안합니다.")
                ),
                "ctaButton", Map.of(
                        "text", "무료로 시작하기",
                        "targetUrl", "/api/v1/signup"
                )
        );

        return ResponseEntity.ok(ApiResponse.success("온보딩 정보 조회 성공", data));
    }
}
