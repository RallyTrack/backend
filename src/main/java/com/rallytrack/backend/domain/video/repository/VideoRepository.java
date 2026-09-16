package com.rallytrack.backend.domain.video.repository;

import com.rallytrack.backend.domain.video.entity.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoRepository extends JpaRepository<Video, Long> {
    java.util.Optional<Video> findByVideoIdAndUserIdAndDeletedAtIsNullAndVideoStatusNot(
            Long videoId, Long userId, String videoStatus);

    List<Video> findByUserIdAndVideoStatusNotOrderByUploadDateDesc(Long userId, String videoStatus);
    int countByUserIdAndVideoStatusNot(Long userId, String videoStatus);
}
