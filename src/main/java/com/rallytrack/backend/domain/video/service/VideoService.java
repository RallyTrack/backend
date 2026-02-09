package com.rallytrack.backend.domain.video.service;

import com.rallytrack.backend.domain.user.entity.User;
import com.rallytrack.backend.domain.user.repository.UserRepository;
import com.rallytrack.backend.domain.video.dto.VideoDetailResponse;
import com.rallytrack.backend.domain.video.dto.VideoUploadRequest;
import com.rallytrack.backend.domain.video.dto.VideoUploadResponse;
import com.rallytrack.backend.domain.video.entity.TimelineEvent;
import com.rallytrack.backend.domain.video.entity.Video;
import com.rallytrack.backend.domain.video.repository.TimelineEventRepository;
import com.rallytrack.backend.domain.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final UserRepository userRepository;

    @Transactional
    public VideoUploadResponse uploadVideo(Long userId, VideoUploadRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Video video = Video.builder()
                .title(request.getTitle())
                .matchDate(request.getMatchDate() != null
                        ? LocalDate.parse(request.getMatchDate())
                        : LocalDate.now())
                .videoStatus("PROCESSING")
                .user(user)
                .build();

        Video saved = videoRepository.save(video);

        return VideoUploadResponse.builder()
                .videoId(saved.getVideoId())
                .title(saved.getTitle())
                .uploadDate(saved.getUploadDate()
                        .format(DateTimeFormatter.ISO_LOCAL_DATE))
                .status(saved.getVideoStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public VideoDetailResponse getVideoDetail(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("영상을 찾을 수 없습니다."));

        List<TimelineEvent> events = timelineEventRepository
                .findByVideoVideoIdOrderByTimestampAsc(videoId);

        List<VideoDetailResponse.TimelineEventDto> eventDtos = events.stream()
                .map(e -> VideoDetailResponse.TimelineEventDto.builder()
                        .eventId(e.getEventId())
                        .timestamp(e.getTimestamp())
                        .displayTime(e.getDisplayTime())
                        .type(e.getEventType())
                        .title(e.getEventTitle())
                        .description(e.getEventDescription())
                        .build())
                .collect(Collectors.toList());

        return VideoDetailResponse.builder()
                .videoInfo(VideoDetailResponse.VideoInfo.builder()
                        .videoId(video.getVideoId())
                        .title(video.getTitle())
                        .videoUrl(video.getS3Url())
                        .thumbnailUrl(video.getThumbnailUrl())
                        .duration(video.getDuration())
                        .build())
                .matchSummary(VideoDetailResponse.MatchSummary.builder()
                        .matchScore(video.getMatchScore())
                        .build())
                .timelineEvents(eventDtos)
                .build();
    }
}
