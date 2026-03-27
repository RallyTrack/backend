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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private static final String S3_BUCKET = "rallytrack-videos";
    private static final String S3_REGION = "us-east-1";

    // ── 영상 업로드 ──────────────────────────────────────────

    @Transactional
    public VideoUploadResponse uploadVideo(Long userId, MultipartFile videoFile,
                                           String title, String matchDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        LocalDate parsedDate = LocalDate.parse(matchDate);

        String s3Url;
        try {
            s3Url = s3Service.upLoadFile(videoFile);
        } catch (IOException e) {
            throw new RuntimeException("영상 파일 업로드에 실패했습니다.");
        }

        Video video = Video.builder()
                .title(title)
                .s3Url(s3Url)
                .matchDate(parsedDate)
                .videoStatus("PROCESSING")
                .user(user)
                .build();

        Video saved = videoRepository.save(video);

        // AI 서버 호출
        try {
            String presignedUrl = s3Service.generatePresignedUrl(s3Url);

            String skeletonKey      = "skeletons/" + UUID.randomUUID() + "_skeleton.mp4";
            String skeletonUploadUrl = s3Service.generatePresignedUploadUrl(skeletonKey);
            String skeletonVideoUrl  = buildS3Url(skeletonKey);

            String minimapKey      = "minimaps/" + UUID.randomUUID() + "_minimap.mp4";
            String minimapUploadUrl = s3Service.generatePresignedUploadUrl(minimapKey);
            String minimapVideoUrl  = buildS3Url(minimapKey);

            Map<String, Object> analyzeRequest = new HashMap<>();
            analyzeRequest.put("videoId",           saved.getVideoId());
            analyzeRequest.put("s3Url",             presignedUrl);
            analyzeRequest.put("skeletonUploadUrl", skeletonUploadUrl);
            analyzeRequest.put("skeletonVideoUrl",  skeletonVideoUrl);
            analyzeRequest.put("minimapUploadUrl",  minimapUploadUrl);
            analyzeRequest.put("minimapVideoUrl",   minimapVideoUrl);

            restTemplate.postForEntity(
                    "http://localhost:8000/analyze",
                    analyzeRequest,
                    String.class
            );
        } catch (Exception e) {
            // AI 서버 호출 실패해도 업로드 자체는 성공으로 처리
        }

        return VideoUploadResponse.builder()
                .videoId(saved.getVideoId())
                .title(saved.getTitle())
                .uploadDate(saved.getUploadDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .status(saved.getVideoStatus())
                .build();
    }

    // ── 영상 상세 조회 ───────────────────────────────────────

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
                        .type(e.getEventType().name())
                        .title(e.getEventTitle())
                        .description(e.getEventDescription())
                        .hitNumber(e.getHitNumber())
                        .player(e.getPlayer())
                        .build())
                .collect(Collectors.toList());

        String skeletonUrl = video.getSkeletonVideoUrl() != null
                ? s3Service.generatePresignedUrl(video.getSkeletonVideoUrl())
                : null;

        String minimapUrl = video.getMinimapVideoUrl() != null
                ? s3Service.generatePresignedUrl(video.getMinimapVideoUrl())
                : null;

        return VideoDetailResponse.builder()
                .videoInfo(VideoDetailResponse.VideoInfo.builder()
                        .videoId(video.getVideoId())
                        .title(video.getTitle())
                        .videoUrl(s3Service.generatePresignedUrl(video.getS3Url()))
                        .skeletonVideoUrl(skeletonUrl)
                        .minimapVideoUrl(minimapUrl)
                        .thumbnailUrl(video.getThumbnailUrl())
                        .durationSeconds(video.getDurationSeconds())
                        .build())
                .matchSummary(VideoDetailResponse.MatchSummary.builder()
                        .matchScore(video.getMatchScore())
                        .build())
                .timelineEvents(eventDtos)
                .build();
    }

    // ── 영상 삭제 ────────────────────────────────────────────

    @Transactional
    public void deleteVideo(Long userId, Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new IllegalArgumentException("영상을 찾을 수 없습니다."));

        if (!video.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 영상에 대한 삭제 권한이 없습니다.");
        }

        timelineEventRepository.deleteByVideoVideoId(videoId);
        analysisResultRepository.deleteByVideoVideoId(videoId);

        try {
            s3Service.deleteFile(video.getS3Url());
        } catch (Exception e) {
            // S3 삭제 실패해도 DB 삭제는 진행
        }

        video.setDeletedAt(LocalDateTime.now());
        video.setVideoStatus("DELETED");
        videoRepository.save(video);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────

    private String buildS3Url(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s", S3_BUCKET, S3_REGION, key);
    }
}
