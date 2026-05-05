package com.rallytrack.backend.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class StrokeTypesDto {
    private Integer smash;
    private Integer clear;
    private Integer drop;
    private Integer drive;
    private Integer serve;
    private Integer net;
    private Integer others;
}
