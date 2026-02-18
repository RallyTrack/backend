package com.rallytrack.backend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {
    @Email
    @NotBlank
    private String email;

    @Schema(description = "비밀번호 (8~20자), 특수기호나 대소문자 제약은 없음", example = "mypass1234!")
    @Size(min = 8, max = 20)
    private String password;

    @NotBlank
    private String nickname;
}
