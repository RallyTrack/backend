package com.rallytrack.backend;

import com.rallytrack.backend.config.*;
import com.rallytrack.backend.domain.briefing.GeminiClient;
import com.rallytrack.backend.domain.user.entity.*;
import com.rallytrack.backend.domain.user.repository.*;
import com.rallytrack.backend.domain.user.service.UserService;
import com.rallytrack.backend.domain.video.entity.Video;
import com.rallytrack.backend.domain.video.repository.VideoRepository;
import com.rallytrack.backend.domain.analysis.repository.AnalysisResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SecurityHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired VideoRepository videos;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository tokens;
    @Autowired AnalysisResultRepository analyses;
    @Autowired UserService userService;
    @Autowired ObjectMapper mapper;
    @MockitoBean S3Service storage;
    @MockitoBean SlackNotifier slack;
    @MockitoBean RestTemplate ai;
    @MockitoBean GeminiClient gemini;
    User owner, other;
    @BeforeEach void setup() {
        owner = users.save(User.builder().email(UUID.randomUUID()+"@test.invalid").build());
        other = users.save(User.builder().email(UUID.randomUUID()+"@test.invalid").build());
        when(storage.generatePresignedUrl(anyString())).thenAnswer(i -> "https://media.test/"+i.getArgument(0));
        when(gemini.model()).thenReturn("test-model"); when(gemini.generate(anyString())).thenReturn("합성 브리핑");
    }
    String auth(User u) { return "Bearer " + jwt.generateAccessToken(u.getId(), u.getEmail()); }
    Video video(User u, String status) {
        return videos.save(Video.builder().title("synthetic").user(u).videoStatus(status)
                .s3Url("videos/test.mp4").thumbnailUrl("videos/test.jpg").skeletonVideoUrl("skeletons/test.mp4")
                .minimapVideoUrl("minimaps/test.mp4").build());
    }
    @Test void foreignMissingDeletedAndOrphanVideosHaveIdentical404WithoutSigningOrProviderCalls() throws Exception {
        var foreign = video(other, "COMPLETED");
        var deletedStatus = video(owner, "DELETED");
        var deletedAt = video(owner, "COMPLETED"); deletedAt.setDeletedAt(LocalDateTime.now()); videos.save(deletedAt);
        var orphan = video(null, "COMPLETED");
        for (long id : List.of(foreign.getVideoId(), deletedStatus.getVideoId(), deletedAt.getVideoId(), orphan.getVideoId(), Long.MAX_VALUE)) {
            for (String path : List.of("/api/v1/videos/", "/api/v1/analysis/")) {
                mvc.perform(get(path+id).header("Authorization", auth(owner))).andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
            }
            mvc.perform(post("/api/v1/videos/"+id+"/briefing").header("Authorization", auth(owner))
                    .contentType("application/json").content("{\"player\":\"top\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
        }
        verifyNoInteractions(storage, gemini);
    }
    @Test void ownedDetailSignsAllFourObjects() throws Exception {
        var v = video(owner, "COMPLETED");
        mvc.perform(get("/api/v1/videos/"+v.getVideoId()).header("Authorization", auth(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.videoInfo.videoUrl").value("https://media.test/videos/test.mp4"));
        verify(storage, times(4)).generatePresignedUrl(anyString());
    }
    @Test void reportStateIsOnlyVisibleToOwner() throws Exception {
        for (String state : List.of("PROCESSING", "FAILED", "COMPLETED")) {
            var v = video(owner, state);
            int code = state.equals("PROCESSING") ? 404 : 409;
            String error = state.equals("PROCESSING") ? "ANALYSIS_NOT_READY" : state.equals("FAILED") ? "ANALYSIS_FAILED" : "ANALYSIS_RESULT_UNAVAILABLE";
            mvc.perform(get("/api/v1/analysis/"+v.getVideoId()).header("Authorization", auth(owner)))
                    .andExpect(status().is(code)).andExpect(jsonPath("$.errorCode").value(error));
            mvc.perform(post("/api/v1/videos/"+v.getVideoId()+"/briefing").header("Authorization", auth(owner))
                    .contentType("application/json").content("{\"player\":\"bottom\"}")).andExpect(status().is(code));
        }
        verifyNoInteractions(gemini);
    }
    @Test void refreshTokenCannotReadUploadChangeScoreDeleteOrGenerateEvenAfterLogout() throws Exception {
        String refresh = jwt.generateRefreshToken(owner.getId(), owner.getEmail());
        tokens.save(RefreshToken.builder().user(owner).token(refresh).expiresAt(LocalDateTime.now().plusDays(1)).build());
        userService.logout(refresh);
        assertThatThrownBy(() -> userService.refreshToken(refresh)).isInstanceOf(IllegalArgumentException.class);
        for (var request : List.of(get("/api/v1/videos/1"), get("/api/v1/analysis/1"), post("/api/v1/videos"),
                delete("/api/v1/videos/1"), patch("/api/v1/videos/1/score"), post("/api/v1/videos/1/briefing"))) {
            mvc.perform(request.header("Authorization", "Bearer "+refresh)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(storage, ai, gemini);
    }
    @Test void refreshEndpointRejectsAccessTokenEvenIfItIsInDatabase() throws Exception {
        String access = jwt.generateAccessToken(owner.getId(), owner.getEmail());
        tokens.save(RefreshToken.builder().user(owner).token(access).expiresAt(LocalDateTime.now().plusDays(1)).build());
        assertThatThrownBy(() -> userService.refreshToken(access)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validRefreshStillIssuesAccessToken() {
        String refresh = jwt.generateRefreshToken(owner.getId(), owner.getEmail());
        tokens.save(RefreshToken.builder().user(owner).token(refresh).expiresAt(LocalDateTime.now().plusDays(1)).build());
        var response = userService.refreshToken(refresh);
        assertThat(jwt.getUserId(response.getAccessToken())).isEqualTo(owner.getId());
        assertThat(jwt.isValidRefreshToken(response.getRefreshToken())).isTrue();
    }
    @Test void invalidUploadCausesNoObjectDatabaseOrAiWrites() throws Exception {
        long before = videos.count();
        mvc.perform(multipart("/api/v1/videos")
                .file(new MockMultipartFile("videoFile", "payload.mp4", "video/mp4", "<html>marker</html>".getBytes()))
                .file(new MockMultipartFile("thumbnailImage", "image.png", "image/png", new byte[]{1}))
                .param("title", "synthetic").param("courtCorners", corners()).header("Authorization", auth(owner)))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(videos.count()).isEqualTo(before); verifyNoInteractions(storage, ai);
    }
    static String corners() { return "{\"topLeft\":{\"x\":0,\"y\":0},\"topRight\":{\"x\":100,\"y\":0},\"bottomLeft\":{\"x\":0,\"y\":100},\"bottomRight\":{\"x\":100,\"y\":100}}"; }
    @Test void uploadCallbackReportAndBriefingPreserveObjectKeyContract() throws Exception {
        byte[] clip;
        try (var input = getClass().getResourceAsStream("/media/sample.mp4")) { clip = input.readAllBytes(); }
        var image = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(16,16,1), "png", image);
        when(storage.uploadMedia(any())).thenReturn("videos/original.mp4", "videos/thumbnail.jpg");
        when(storage.generateAiPresignedUrl(any())).thenReturn("http://ai-media.test/original?signature=test");
        when(storage.generateAiPresignedUploadUrl(any())).thenReturn("http://ai-media.test/output?signature=test");
        var response = mvc.perform(multipart("/api/v1/videos")
                .file(new MockMultipartFile("videoFile", "sample.mp4", "video/mp4", clip))
                .file(new MockMultipartFile("thumbnailImage", "image.png", "image/png", image.toByteArray()))
                .param("title", "synthetic").param("courtCorners", corners()).param("mode", "amateur")
                .header("Authorization", auth(owner))).andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(response.getResponse().getContentAsString()).path("data").path("videoId").asLong();
        var stored = videos.findById(id).orElseThrow(); assertThat(stored.getS3Url()).isEqualTo("videos/original.mp4");
        var request = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(ai).postForEntity(anyString(), request.capture(), eq(String.class));
        assertThat(request.getValue().get("s3Url")).isEqualTo("http://ai-media.test/original?signature=test");
        String skeleton = request.getValue().get("skeletonVideoUrl").toString();
        String minimap = request.getValue().get("minimapVideoUrl").toString();
        assertThat(skeleton).startsWith("skeletons/").endsWith(".mp4");
        mvc.perform(post("/api/v1/analysis/complete").header("X-Internal-Token", "test-callback-secret")
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("videoId", id, "videoFps", 10,
                        "totalHits", 0, "hitsData", List.of(), "skeletonVideoUrl", skeleton, "minimapVideoUrl", minimap))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/analysis/"+id).header("Authorization", auth(owner))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/videos/"+id+"/briefing").header("Authorization", auth(owner))
                .contentType("application/json").content("{\"player\":\"top\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.text").value("합성 브리핑"));
        verify(gemini).generate(contains("Top Player"));
        assertThat(stored.getSkeletonVideoUrl()).isEqualTo(skeleton);
        assertThat(stored.getMinimapVideoUrl()).isEqualTo(minimap);
    }
}
