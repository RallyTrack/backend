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

    // 스트로크 분류 결과 (null 허용 — 분류기 미설치 시)
    @Column(name = "stroke_type", length = 20)
    private String strokeType;

    // 타격 시점 선수 중심 좌표 (0.0~1.0 정규화, AI 서버 제공 시에만 저장)
    @Column(name = "player_x")
    private Float playerX;

    @Column(name = "player_y")
    private Float playerY;
}
