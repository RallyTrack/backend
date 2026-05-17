package com.rallytrack.backend.domain.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AbilityMetricsDto {
    private int aggression;    // 0-100
    private int rally;         // 0-100
    private int defense;       // 0-100
    private int mobility;      // 0-100
    private int consistency;   // 0-100
}
