package com.rallytrack.backend.domain.user.service;

import com.rallytrack.backend.config.JwtUtil;
import com.rallytrack.backend.domain.user.dto.LoginRequest;
import com.rallytrack.backend.domain.user.dto.LoginResponse;
import com.rallytrack.backend.domain.user.dto.SignupRequest;
import com.rallytrack.backend.domain.user.entity.User;
import com.rallytrack.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));

        // 비밀번호 검증 (테스트용 평문 비교, 추후 BCrypt 적용)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 마지막 로그인 시간 업데이트
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .lastLogin(user.getLastLogin()
                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z")
                        .build())
                .build();
    }

    @Transactional
    public LoginResponse signup(SignupRequest request) {
        // 1. 이메일 중복 체크
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 사용중인 이메일 입니다.");
        }

        // 2. User 생성 및 비밀번호 암호화
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        // 3. DB 저장
        User saved = userRepository.save(user);

        // 4. 토큰 발급 + 응답
        String accessToken = jwtUtil.generateAccessToken(saved.getId(), saved.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(saved.getId(), saved.getEmail());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(LoginResponse.UserInfo.builder()
                        .id(saved.getId())
                        .email(saved.getEmail())
                        .nickname(saved.getNickname())
                        .lastLogin(null)
                        .build())
                .build();
    }
}
