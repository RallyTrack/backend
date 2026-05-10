package com.rallytrack.backend.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PlayerReportDto {
    private PositionAnalysisDto positionAnalysis;
    private StrokeTypesDto strokeTypes;
    private AbilityMetricsDto abilityMetrics;
    private AiCoachingDto aiCoaching;
}
