package com.rallytrack.backend.domain.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rallytrack.backend.config.S3Service;
import com.rallytrack.backend.config.SlackNotifier;
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
import org.springframework.beans.factory.annotation.Value;
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
import com.rallytrack.backend.domain.analysis.entity.AnalysisResult;
import com.rallytrack.backend.domain.analysis.entity.Hit;

@Service
@RequiredArgsConstructor
public class VideoService {

        private final VideoRepository videoRepository;
        private final TimelineEventRepository timelineEventRepository;
        private final AnalysisResultRepository analysisResultRepository;
        private final UserRepository userRepository;
        private final S3Service s3Service;
        private final SlackNotifier slackNotifier;
        private final RestTemplate restTemplate;
        private final ObjectMapper objectMapper = new ObjectMapper();

        // AI 분석 서버 주소 (env: AI_SERVER_URL)
        @Value("${ai.server.url}")
        private String aiServerUrl;

        // ── 영상 업로드 ──────────────────────────────────────────

        @Transactional
        public VideoUploadResponse uploadVideo(Long userId, MultipartFile videoFile,
                        String title, MultipartFile thumbnailImage, String courtCorners,
                        Integer durationSeconds, String mode) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
                String analysisMode = normalizeAnalysisMode(mode);

                String thumbnailS3Url = null;
                try {
                        thumbnailS3Url = s3Service.upLoadFile(thumbnailImage);
                } catch (IOException e) {
                        System.out.println("썸네일 업로드 실패: " + e.getMessage());
                }

                String s3Url;
                try {
                        s3Url = s3Service.upLoadFile(videoFile);
                } catch (IOException e) {
                        throw new RuntimeException("영상 파일 업로드에 실패했습니다.");
                }

                Map<String, Map<String, Integer>> corners;
                try {
                        corners = objectMapper.readValue(courtCorners, Map.class);
                } catch (Exception e) {
                        throw new RuntimeException("코트 코너 데이터 파싱 실패", e);
                }

                Video video = Video.builder()
                                .title(title)
                                .s3Url(s3Url)
                                .thumbnailUrl(thumbnailS3Url)
                                .matchDate(LocalDate.now())
                                .videoStatus("PROCESSING")
                                .user(user)
                                .courtTopLeftX(corners.get("topLeft").get("x"))
                                .courtTopLeftY(corners.get("topLeft").get("y"))
                                .courtTopRightX(corners.get("topRight").get("x"))
                                .courtTopRightY(corners.get("topRight").get("y"))
                                .courtBottomLeftX(corners.get("bottomLeft").get("x"))
                                .courtBottomLeftY(corners.get("bottomLeft").get("y"))
                                .courtBottomRightX(corners.get("bottomRight").get("x"))
                                .courtBottomRightY(corners.get("bottomRight").get("y"))
                                .netTopLeftX(corners.containsKey("netTopLeft") ? corners.get("netTopLeft").get("x")
                                                : null)
                                .netTopLeftY(corners.containsKey("netTopLeft") ? corners.get("netTopLeft").get("y")
                                                : null)
                                .netTopRightX(corners.containsKey("netTopRight") ? corners.get("netTopRight").get("x")
                                                : null)
                                .netTopRightY(corners.containsKey("netTopRight") ? corners.get("netTopRight").get("y")
                                                : null)
                                .durationSeconds(
                                                durationSeconds != null && durationSeconds > 0 ? durationSeconds : null)
                                .build();

                Video saved = videoRepository.save(video);

                slackNotifier.notify(String.format(
                                "📤 영상 업로드 — videoId=%d, 제목: %s, mode=%s, 크기: %.1fMB, 재생시간: %ds",
                                saved.getVideoId(), title, analysisMode,
                                videoFile.getSize() / 1024.0 / 1024.0,
                                durationSeconds != null ? durationSeconds : 0));

                // AI 서버 호출
                try {
                        // AI 서버용 URL은 LAN presigner로 서명 (브라우저용 공개 도메인과 분리)
                        String presignedUrl = s3Service.generateAiPresignedUrl(s3Url);

                        // DB·콜백에는 object key만 전달한다 (S3Service 주석 참고)
                        String skeletonKey = "skeletons/" + UUID.randomUUID() + "_skeleton.mp4";
                        String skeletonUploadUrl = s3Service.generateAiPresignedUploadUrl(skeletonKey);

                        String minimapKey = "minimaps/" + UUID.randomUUID() + "_minimap.mp4";
                        String minimapUploadUrl = s3Service.generateAiPresignedUploadUrl(minimapKey);

                        Map<String, Object> analyzeRequest = new HashMap<>();
                        analyzeRequest.put("videoId", saved.getVideoId());
                        analyzeRequest.put("s3Url", presignedUrl);
                        analyzeRequest.put("skeletonUploadUrl", skeletonUploadUrl);
                        analyzeRequest.put("skeletonVideoUrl", skeletonKey);
                        analyzeRequest.put("minimapUploadUrl", minimapUploadUrl);
                        analyzeRequest.put("minimapVideoUrl", minimapKey);
                        analyzeRequest.put("mode", analysisMode);

                        Map<String, Object> courtCornersMap = new HashMap<>();
                        courtCornersMap.put("topLeft",
                                        Map.of("x", saved.getCourtTopLeftX(), "y", saved.getCourtTopLeftY()));
                        courtCornersMap.put("topRight",
                                        Map.of("x", saved.getCourtTopRightX(), "y", saved.getCourtTopRightY()));
                        courtCornersMap.put("bottomLeft",
                                        Map.of("x", saved.getCourtBottomLeftX(), "y", saved.getCourtBottomLeftY()));
                        courtCornersMap.put("bottomRight",
                                        Map.of("x", saved.getCourtBottomRightX(), "y", saved.getCourtBottomRightY()));
                        if (corners.containsKey("netTopLeft"))
                                courtCornersMap.put("netTopLeft", corners.get("netTopLeft"));
                        if (corners.containsKey("netTopRight"))
                                courtCornersMap.put("netTopRight", corners.get("netTopRight"));
                        analyzeRequest.put("courtCorners", courtCornersMap);

                        System.out.println("[AI 요청 body] courtCorners: "
                                        + objectMapper.writeValueAsString(courtCornersMap));

                        restTemplate.postForEntity(
                                        aiServerUrl + "/analyze",
                                        analyzeRequest,
                                        String.class);
                        slackNotifier.notify("🚀 AI 분석 요청 전송 완료 — videoId=" + saved.getVideoId()
                                        + " → " + aiServerUrl);
                } catch (Exception e) {
                        e.printStackTrace();
                        // AI 서버 호출 실패해도 업로드 자체는 성공으로 처리
                        // (단, 이 경우 영상이 PROCESSING에 영구히 머물므로 반드시 알림)
                        slackNotifier.notify("⚠️ AI 서버 호출 실패 — videoId=" + saved.getVideoId()
                                        + "\n에러: " + e.getMessage()
                                        + "\n※ 이 영상은 PROCESSING 상태로 남습니다. AI 서버 확인 후 재업로드 필요");
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

                Map<Integer, Float> hitTimeSec = analysisResultRepository.findByVideoVideoId(videoId)
                                .map(AnalysisResult::getHits)
                                .orElse(List.of())
                                .stream()
                                .collect(Collectors.toMap(Hit::getHitNumber, Hit::getTimeSec, (a, b) -> a));

                List<VideoDetailResponse.TimelineEventDto> eventDtos = events.stream()
                                .map(e -> VideoDetailResponse.TimelineEventDto.builder()
                                                .eventId(e.getEventId())
                                                .timestamp(e.getHitNumber() != null
                                                                ? hitTimeSec.getOrDefault(e.getHitNumber(),
                                                                                e.getTimestamp().floatValue())
                                                                : e.getTimestamp().floatValue())
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

                VideoDetailResponse.MatchSummary matchSummary;
                java.util.Optional<AnalysisResult> arOpt = analysisResultRepository.findByVideoVideoId(videoId);
                if (arOpt.isPresent()) {
                        AnalysisResult ar = arOpt.get();
                        matchSummary = VideoDetailResponse.MatchSummary.builder()
                                        .matchScore(video.getMatchScore())
                                        .unknownRallies(ar.getUnknownRallies() != null ? ar.getUnknownRallies() : 0)
                                        .totalRallies(ar.getTotalRallies() != null ? ar.getTotalRallies() : 0)
                                        .build();
                } else {
                        matchSummary = VideoDetailResponse.MatchSummary.builder()
                                        .matchScore(video.getMatchScore())
                                        .unknownRallies(0)
                                        .totalRallies(0)
                                        .build();
                }

                return VideoDetailResponse.builder()
                                .videoInfo(VideoDetailResponse.VideoInfo.builder()
                                                .videoId(video.getVideoId())
                                                .title(video.getTitle())
                                                .videoUrl(s3Service.generatePresignedUrl(video.getS3Url()))
                                                .skeletonVideoUrl(skeletonUrl)
                                                .minimapVideoUrl(minimapUrl)
                                                .thumbnailUrl(video.getThumbnailUrl() != null
                                                                ? s3Service.generatePresignedUrl(video.getThumbnailUrl())
                                                                : null)
                                                .durationSeconds(video.getDurationSeconds())
                                                .build())
                                .matchSummary(matchSummary)
                                .timelineEvents(eventDtos)
                                .build();
        }

        // ── 점수 수동 수정 ───────────────────────────────────────

        @Transactional
        public void updateMatchScore(Long userId, Long videoId, Integer topScore, Integer bottomScore) {
                Video video = videoRepository.findById(videoId)
                                .orElseThrow(() -> new IllegalArgumentException("영상을 찾을 수 없습니다."));

                if (!video.getUser().getId().equals(userId)) {
                        throw new IllegalArgumentException("수정 권한이 없습니다.");
                }

                int top = topScore != null ? Math.max(0, Math.min(topScore, 30)) : 0;
                int bottom = bottomScore != null ? Math.max(0, Math.min(bottomScore, 30)) : 0;

                video.setMatchScore(top + ":" + bottom);
                videoRepository.save(video);

                analysisResultRepository.findByVideoVideoId(videoId).ifPresent(ar -> {
                        ar.setTopPlayerScore(top);
                        ar.setBottomPlayerScore(bottom);
                        String outcome = top > bottom ? "TOP_WIN" : top < bottom ? "BOTTOM_WIN" : "DRAW";
                        ar.setMatchOutcome(outcome);
                        ar.setUnknownRallies(0);
                        analysisResultRepository.save(ar);
                });
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

        private String normalizeAnalysisMode(String mode) {
                return "amateur".equalsIgnoreCase(mode != null ? mode.trim() : "") ? "amateur" : "pro";
        }
}
