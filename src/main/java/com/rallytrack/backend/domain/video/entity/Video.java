package com.rallytrack.backend.domain.video.entity;

import com.rallytrack.backend.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_id")
    private Long videoId;

    @Column(nullable = false)
    private String title;

    // URL 컬럼은 length = 500 명시 (기본값 255로 자동 ALTER 방지)
    @Column(name = "s3_url", length = 500)
    private String s3Url;

    @Column(name = "skeleton_video_url", length = 500)
    private String skeletonVideoUrl;

    @Column(name = "minimap_video_url", length = 500)
    private String minimapVideoUrl;

    @Column(name = "court_top_left_x")
    private Integer courtTopLeftX;

    @Column(name = "court_top_left_y")
    private Integer courtTopLeftY;

    @Column(name = "court_top_right_x")
    private Integer courtTopRightX;

    @Column(name = "court_top_right_y")
    private Integer courtTopRightY;

    @Column(name = "court_bottom_left_x")
    private Integer courtBottomLeftX;

    @Column(name = "court_bottom_left_y")
    private Integer courtBottomLeftY;

    @Column(name = "court_bottom_right_x")
    private Integer courtBottomRightX;

    @Column(name = "court_bottom_right_y")
    private Integer courtBottomRightY;

    @Column(name = "net_top_left_x")
    private Integer netTopLeftX;

    @Column(name = "net_top_left_y")
    private Integer netTopLeftY;

    @Column(name = "net_top_right_x")
    private Integer netTopRightX;

    @Column(name = "net_top_right_y")
    private Integer netTopRightY;


    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    // 영상 길이 (초 단위 정수) — 대시보드 총 분석 시간 집계 시 SUM(duration_seconds) 사용
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "video_status")
    @Builder.Default
    private String videoStatus = "PROCESSING";

    @Column(name = "match_score")
    private String matchScore;

    @Column(name = "match_date")
    @Schema(description = "경기 날짜 (YYYY-MM-DD 형식)", example = "2026-02-22")
    private LocalDate matchDate;

    @CreationTimestamp
    @Column(name = "upload_date", updatable = false)
    private LocalDateTime uploadDate;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
}
