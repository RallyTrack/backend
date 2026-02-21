package com.rallytrack.backend.domain.video.controller;

import com.rallytrack.backend.domain.video.dto.VideoDetailResponse;
import com.rallytrack.backend.domain.video.dto.VideoUploadResponse;
import com.rallytrack.backend.domain.video.service.VideoService;
import com.rallytrack.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Video", description = "영상 API")
@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @Operation(summary = "영상 업로드", description = "영상을 업로드합니다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)    // 이 API는 JSON이 아닌 form-data형식을 받는다 선언
    public ResponseEntity<ApiResponse<VideoUploadResponse>> uploadVideo(
            HttpServletRequest httpRequest,
            @RequestParam("videoFile") @Schema(description = "영상 파일 (최대 500MB)")MultipartFile videoFile,
            @RequestParam("title") String title,
            @RequestParam(value = "matchDate") String matchDate) {

        Long userId = (Long) httpRequest.getAttribute("userId");

        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "영상 이름을 입력해주세요."));
        }

        if (videoFile.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "영상 파일을 선택해주세요"));
        }

        VideoUploadResponse response = videoService.uploadVideo(userId, videoFile, title, matchDate);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("영상이 성공적으로 업로드되었습니다.", response));
    }

    @Operation(summary = "영상 상세 정보 조회", description = "영상 플레이어 정보 및 타임라인 이벤트를 조회합니다.")
    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<VideoDetailResponse>> getVideoDetail(
            @PathVariable Long videoId) {

        VideoDetailResponse response = videoService.getVideoDetail(videoId);
        return ResponseEntity.ok(ApiResponse.success("성공", response));
    }
}
