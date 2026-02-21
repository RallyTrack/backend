package com.rallytrack.backend.domain.video.service;

import com.rallytrack.backend.config.S3Service;
import com.rallytrack.backend.domain.user.entity.User;
import com.rallytrack.backend.domain.user.repository.UserRepository;
import com.rallytrack.backend.domain.video.dto.VideoDetailResponse;
import com.rallytrack.backend.domain.video.dto.VideoUploadResponse;
import com.rallytrack.backend.domain.video.entity.TimelineEvent;
import com.rallytrack.backend.domain.video.entity.Video;
import com.rallytrack.backend.domain.video.repository.TimelineEventRepository;
import com.rallytrack.backend.domain.video.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final UserRepository userRepository;

    private final S3Service s3Service;

    private final RestTemplate restTemplate;

    @Transactional
    public VideoUploadResponse uploadVideo(Long userId, MultipartFile videoFile,
                                           String title,
                                           String matchDate) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 입력값 검증
        LocalDate parsedDate = LocalDate.parse(matchDate);

        // S3 업로드
        String s3Url;

        try {
            s3Url = s3Service.upLoadFile(videoFile);
        } catch (IOException e) {
            throw new RuntimeException("영상 파일 업로드에 실패했습니다.");
        }

        // DB 저장
        Video video = Video.builder()
                .title(title)
                .s3Url(s3Url)
                .matchDate(parsedDate)
                .videoStatus("PROCESSING")
                .user(user)
                .build();

        Video saved = videoRepository.save(video);

        // 분석 서버 호출
        try {
            Map<String, Object> analyzeRequest = Map.of(
                    "videoId", saved.getVideoId(),
                    "s3Url", s3Url
            );
            restTemplate.postForEntity(
                    "http://localhost:8000/analyze",    // 분석서버에 POST요청 보냄, 추후 .yml에 변수로 관리할 수 있도록 변경
                    analyzeRequest,
                    String.class
            );
        } catch (Exception e){
            // 분석서버 호출이 실패해도 업로드 자체는 성공
            // 재시도 로직 추후 추가 예정
        }

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
                        .videoUrl(s3Service.generatePresignedUrl(video.getS3Url()))
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
