package com.rallytrack.backend.domain.video.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VideoUploadResponse {
    private Long videoId;
    private String title;
    private String uploadDate;
    private String status;
    private String analysisMode;
}
