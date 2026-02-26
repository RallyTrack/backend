package com.rallytrack.backend.domain.video.repository;

import com.rallytrack.backend.domain.video.entity.TimelineEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {
    List<TimelineEvent> findByVideoVideoIdOrderByTimestampAsc(Long videoId);
    void deleteByVideoVideoId(Long videoId);
}
