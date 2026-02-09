package com.rallytrack.backend.domain.video.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VideoUploadRequest {
    private String title;
    private String matchDate;
}
