package com.rallytrack.backend.domain.analysis.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hits")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hit_id")
    private Long hitId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id", nullable = false)
    private AnalysisResult analysisResult;

    @Column(name = "hit_number", nullable = false)
    private Integer hitNumber;

    @Column(name = "frame", nullable = false)
    private Integer frame;

    // 소수점 초 단위 (예: 1.500) — AI 서버에서 float으로 전송
    @Column(name = "time_sec", nullable = false)
    private Float timeSec;

    // "pink_top" | "green_bottom"
    @Column(name = "player", nullable = false, length = 20)
    private String player;
}
