package com.rallytrack.backend.domain.user.controller;

import com.rallytrack.backend.domain.user.dto.LoginRequest;
import com.rallytrack.backend.domain.user.dto.LoginResponse;
import com.rallytrack.backend.domain.user.dto.SignupRequest;
import com.rallytrack.backend.domain.user.service.UserService;
import com.rallytrack.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하여 JWT 토큰을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", response));
    }

    @Operation(summary = "회원가입", description = "메일과 비밀번호 , 이름을 입력하여 회원가입, 회원가입 이후 바로 로그인되어있음")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<LoginResponse>> signup(@Valid
                                                             @RequestBody SignupRequest request) {
        LoginResponse response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("회원가입 성공", response));
    }
}
