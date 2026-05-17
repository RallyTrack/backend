package com.rallytrack.backend.domain.video.controller;

import com.rallytrack.backend.domain.video.dto.VideoDetailResponse;
import com.rallytrack.backend.domain.video.dto.VideoUploadResponse;
import com.rallytrack.backend.domain.video.service.VideoService;
import com.rallytrack.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VideoUploadResponse>> uploadVideo(
            HttpServletRequest httpRequest,
            @RequestParam("videoFile") @Schema(description = "영상 파일 (최대 500MB)") MultipartFile videoFile,
            @RequestParam("title") String title,
            @RequestParam("thumbnailImage") MultipartFile thumbnailImage,
            @RequestParam("courtCorners") String courtCorners,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @Parameter(
                    description = "분석 모드: pro=프로 선수 기준, amateur=아마추어 기준",
                    schema = @Schema(
                            allowableValues = {"pro", "amateur"},
                            defaultValue = "amateur",
                            example = "amateur"
                    )
            )
            @RequestParam(value = "mode", required = false, defaultValue = "amateur") String mode) {
        Long userId = (Long) httpRequest.getAttribute("userId");

        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "영상 이름을 입력해주세요."));
        }

        if (videoFile.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "영상 파일을 선택해주세요"));
        }

        VideoUploadResponse response = videoService.uploadVideo(userId, videoFile, title, thumbnailImage, courtCorners,
                durationSeconds, mode);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("영상이 성공적으로 업로드되었습니다.", response));
    }

    @Operation(summary = "영상 상세 정보 조회", description = "영상 플레이어 정보 및 타임라인 이벤트를 조회합니다.")
    @GetMapping("/{videoId}")
    public ResponseEntity<ApiResponse<VideoDetailResponse>> getVideoDetail(
            @PathVariable("videoId") Long videoId) {

        VideoDetailResponse response = videoService.getVideoDetail(videoId);
        return ResponseEntity.ok(ApiResponse.success("성공", response));
    }

    @Operation(summary = "점수 수동 수정", description = "사용자가 매치 스코어를 직접 수정합니다.")
    @PatchMapping("/{videoId}/score")
    public ResponseEntity<ApiResponse<Void>> updateScore(
            HttpServletRequest httpRequest,
            @PathVariable("videoId") Long videoId,
            @RequestBody ScoreUpdateRequest request) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        videoService.updateMatchScore(userId, videoId, request.getTopScore(), request.getBottomScore());
        return ResponseEntity.ok(ApiResponse.success("점수가 수정되었습니다.", null));
    }

    @Operation(summary = "영상 삭제", description = "영상을 소프트 삭제합니다. 연관된 분석 결과와 타임라인 이벤트도 함께 삭제됩니다.")
    @DeleteMapping("/{videoId}")
    public ResponseEntity<ApiResponse<Void>> deleteVideo(
            HttpServletRequest httpRequest,
            @PathVariable("videoId") Long videoId) {

        Long userId = (Long) httpRequest.getAttribute("userId");
        videoService.deleteVideo(userId, videoId);
        return ResponseEntity.ok(ApiResponse.success("영상이 삭제되었습니다.", null));
    }
}

@Getter
@NoArgsConstructor
class ScoreUpdateRequest {
    private Integer topScore;
    private Integer bottomScore;
}
