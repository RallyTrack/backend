package com.rallytrack.backend.domain.video.service;

import com.rallytrack.backend.domain.video.entity.Video;
import com.rallytrack.backend.domain.video.repository.VideoRepository;
import com.rallytrack.backend.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VideoAccessService {
    private final VideoRepository videos;

    @Transactional(readOnly = true)
    public Video requireOwned(Long userId, Long videoId) {
        if (userId == null || userId <= 0 || videoId == null || videoId <= 0) throw ApiException.notFound();
        return videos.findByVideoIdAndUserIdAndDeletedAtIsNullAndVideoStatusNot(videoId, userId, "DELETED")
                .orElseThrow(ApiException::notFound);
    }
}
