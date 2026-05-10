package com.rallytrack.backend.domain.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SummaryDto {
    private Integer myScore;
    private Integer opponentScore;
    private String matchOutcome;    // WIN, LOSE, DRAW
    private Integer totalStrokeCount;
    private String matchTime;
}
