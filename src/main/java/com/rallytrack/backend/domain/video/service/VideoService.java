package com.rallytrack.backend.domain.video.service;

import com.rallytrack.backend.config.S3Service;
import com.rallytrack.backend.domain.analysis.repository.AnalysisResultRepository;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final AnalysisResultRepository analysisResultRepository;
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

        // 분석 서버 호출 (presigned URL로 전달)
        try {
            String presignedUrl = s3Service.generatePresignedUrl(s3Url);
            Map<String, Object> analyzeRequest = Map.of(
                    "videoId", saved.getVideoId(),
                    "s3Url", presignedUrl
            );
            restTemplate.postForEntity(
                    "http://localhost:8000/analyze",
                    analyzeRequest,
                    String.class
            );
        } catch (Exception e) {
            // 분석서버 호출이 실패해도 업로드 자체는 성공
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

    @Transactional
    public void deleteVideo(Long userId, Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("영상을 찾을 수 없습니다."));

        // 소유권 검증
        if (!video.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 영상에 대한 삭제 권한이 없습니다.");
        }

        // 1. 타임라인 이벤트 삭제
        timelineEventRepository.deleteByVideoVideoId(videoId);

        // 2. 분석 결과 삭제
        analysisResultRepository.deleteByVideoVideoId(videoId);

        // 3. S3 파일 삭제
        try {
            s3Service.deleteFile(video.getS3Url());
        } catch (Exception e) {
            // S3 삭제 실패해도 DB 삭제는 진행
        }

        // 4. 영상 삭제 (소프트 삭제)
        video.setDeletedAt(LocalDateTime.now());
        video.setVideoStatus("DELETED");
        videoRepository.save(video);
    }
}
