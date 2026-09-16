package com.rallytrack.backend.domain.briefing;

import com.rallytrack.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/videos")
public class BriefingController {
    private final BriefingService service;
    public record Request(@NotNull @Pattern(regexp = "top|bottom") String player) {}
    public record Response(Long videoId, String player, String text) {}

    @PostMapping("/{videoId}/briefing")
    public ApiResponse<Response> generate(@RequestAttribute("userId") Long userId,
            @PathVariable Long videoId, @Valid @RequestBody Request request) {
        return ApiResponse.success("브리핑 생성 성공", new Response(videoId, request.player(),
                service.generate(userId, videoId, request.player())));
    }
}
