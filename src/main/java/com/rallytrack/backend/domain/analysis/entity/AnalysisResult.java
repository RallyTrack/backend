package com.rallytrack.backend.domain.analysis.entity;

import com.rallytrack.backend.domain.video.entity.Video;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "my_score")
    private Integer myScore;

    @Column(name = "opponent_score")
    private Integer opponentScore;

    @Column(name = "match_outcome")
    private String matchOutcome;

    @Column(name = "total_stroke_count")
    private Integer totalStrokeCount;

    @Column(name = "match_time")
    private String matchTime;

    @Column(name = "heatmap_data", columnDefinition = "JSON")
    private String heatmapData;

    @Column(name = "stroke_types", columnDefinition = "JSON")
    private String strokeTypes;

    @Column(name = "ability_metrics", columnDefinition = "JSON")
    private String abilityMetrics;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private Video video;
}
