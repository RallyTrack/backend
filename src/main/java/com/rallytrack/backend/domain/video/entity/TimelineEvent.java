package com.rallytrack.backend.domain.video.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "timeline_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(nullable = false)
    private Integer timestamp;

    @Column(name = "display_time")
    private String displayTime;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_title")
    private String eventTitle;

    @Column(name = "event_description")
    private String eventDescription;

    @Column(name = "event_score")
    private String eventScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private Video video;
}
