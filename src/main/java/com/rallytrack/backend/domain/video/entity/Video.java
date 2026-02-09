package com.rallytrack.backend.domain.video.entity;

import com.rallytrack.backend.domain.user.entity.User;
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

    @Column(name = "s3_url")
    private String s3Url;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column
    private Integer duration;

    @Column(name = "video_status")
    @Builder.Default
    private String videoStatus = "PROCESSING";

    @Column(name = "match_score")
    private String matchScore;

    @Column(name = "match_date")
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
