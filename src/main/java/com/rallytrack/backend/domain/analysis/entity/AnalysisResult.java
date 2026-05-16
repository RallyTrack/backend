package com.rallytrack.backend.domain.analysis.entity;

import com.rallytrack.backend.domain.video.entity.Video;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analysis_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", unique = true)
    private Video video;

    // AI 서버 파이프라인 원본 결과
    @Column(name = "video_fps")
    private Float videoFps;

    @Column(name = "total_hits")
    private Integer totalHits;

    // 개별 타점은 hits 테이블에 저장 (hits_data에서 파생)
    @OneToMany(mappedBy = "analysisResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Hit> hits = new ArrayList<>();

    // AI 서버 player_metrics 기반 홈 복귀율
    @Column(name = "home_return_rate_top")
    private Float homeReturnRateTop;

    @Column(name = "home_return_rate_bottom")
    private Float homeReturnRateBottom;

    // hits_data 기반 파생 점수 (코트 상단/하단 기준)
    @Column(name = "top_player_score")
    private Integer topPlayerScore;

    @Column(name = "bottom_player_score")
    private Integer bottomPlayerScore;

    // "TOP_WIN" | "BOTTOM_WIN" | "DRAW"
    @Column(name = "match_outcome")
    private String matchOutcome;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
