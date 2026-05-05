package com.rallytrack.backend.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AbilityMetricsDto {
    private Integer smash;
    @JsonProperty("AvgRallyTime")
    private Integer avgRallyTime;
    private Integer speed;
    private Integer distance;
    private Integer errorRate;
}
