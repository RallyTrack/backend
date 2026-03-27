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

    // 이벤트 발생 시점 (초 단위 정수, 대시보드 집계 및 타임라인 탐색용)
    @Column(nullable = false)
    private Integer timestamp;

    // 화면 표시용 시간 문자열 (예: "1:23")
    @Column(name = "display_time")
    private String displayTime;

    // Enum으로 관리 — 오타 방지 및 타입 안정성
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;

    @Column(name = "event_title")
    private String eventTitle;

    @Column(name = "event_description")
    private String eventDescription;

    // HIT 이벤트일 때만 값 존재
    @Column(name = "hit_number")
    private Integer hitNumber;

    // "pink_top" | "green_bottom" — HIT 이벤트일 때만 값 존재
    @Column(name = "player", length = 20)
    private String player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id")
    private Video video;
}
