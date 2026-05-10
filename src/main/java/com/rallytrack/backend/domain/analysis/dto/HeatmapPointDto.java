package com.rallytrack.backend.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class HeatmapPointDto {
    private Double x;
    private Double y;
    private Double value;
    private Double timeSec;
}
