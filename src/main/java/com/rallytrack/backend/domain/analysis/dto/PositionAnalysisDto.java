package com.rallytrack.backend.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class PositionAnalysisDto {
    private List<HeatmapPointDto> heatmapData;
}
