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

    @Column(name = "time_sec", nullable = false)
    private Float timeSec;

    // "top" | "bottom"
    @Column(name = "player", nullable = false, length = 20)
    private String player;

    @Column(name = "stroke_type", length = 20)
    private String strokeType;

    // 타격 시점 선수 중심 좌표 (0.0~1.0 정규화, AI 서버 제공 시에만 저장)
    @Column(name = "player_x")
    private Float playerX;

    @Column(name = "player_y")
    private Float playerY;

    // 타격 시점 셔틀콕의 미니맵 좌표 (0~100, 히트맵 SVG 좌표계와 동일)
    // AI 서버 pipeline_service.py Step 7.9에서 frame_to_minimap() 후 주입
    @Column(name = "minimap_x")
    private Float minimapX;

    @Column(name = "minimap_y")
    private Float minimapY;
}
