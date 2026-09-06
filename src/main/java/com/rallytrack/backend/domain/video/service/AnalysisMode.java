package com.rallytrack.backend.domain.video.service;

/** 영상 업로드에서 받은 분석 모드를 AI 서버 계약 값으로 정규화합니다. */
public final class AnalysisMode {

    private AnalysisMode() {
    }

    public static String normalize(String mode) {
        return "amateur".equalsIgnoreCase(mode != null ? mode.trim() : "")
                ? "amateur"
                : "pro";
    }
}
