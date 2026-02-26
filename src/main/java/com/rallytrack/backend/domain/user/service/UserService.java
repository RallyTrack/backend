package com.rallytrack.backend.domain.user.service;

import com.rallytrack.backend.config.JwtUtil;
import com.rallytrack.backend.domain.user.dto.LoginRequest;
import com.rallytrack.backend.domain.user.dto.LoginResponse;
import com.rallytrack.backend.domain.user.dto.SignupRequest;
import com.rallytrack.backend.domain.user.entity.RefreshToken;
import com.rallytrack.backend.domain.user.entity.User;
import com.rallytrack.backend.domain.user.repository.RefreshTokenRepository;
import com.rallytrack.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));

        // 비밀번호 검증 (BCrypt 적용)
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 마지막 로그인 시간 업데이트
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // 기존 리프레시 토큰 삭제 (기기당 하나만 유지)
        refreshTokenRepository.deleteByUserId(user.getId());

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String refreshTokenStr = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

        // 리프레시 토큰 DB 저장
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration))
                .build();
        refreshTokenRepository.save(refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
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

        // 4. 토큰 발급
        String accessToken = jwtUtil.generateAccessToken(saved.getId(), saved.getEmail());
        String refreshTokenStr = jwtUtil.generateRefreshToken(saved.getId(), saved.getEmail());

        // 5. 리프레시 토큰 DB 저장
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .user(saved)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration))
                .build();
        refreshTokenRepository.save(refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .user(LoginResponse.UserInfo.builder()
                        .id(saved.getId())
                        .email(saved.getEmail())
                        .nickname(saved.getNickname())
                        .lastLogin(null)
                        .build())
                .build();
    }

    @Transactional
    public LoginResponse refreshToken(String refreshTokenStr) {
        // 1. DB에서 리프레시 토큰 조회
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다."));

        // 2. 만료 확인
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("리프레시 토큰이 만료되었습니다. 다시 로그인해주세요.");
        }

        // 3. JWT 자체 검증
        if (!jwtUtil.isValid(refreshTokenStr)) {
            refreshTokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("리프레시 토큰이 유효하지 않습니다.");
        }

        User user = refreshToken.getUser();

        // 4. 기존 리프레시 토큰 삭제
        refreshTokenRepository.delete(refreshToken);

        // 5. 새 토큰 발급
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshTokenStr = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());

        // 6. 새 리프레시 토큰 DB 저장
        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(newRefreshTokenStr)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiration))
                .build();
        refreshTokenRepository.save(newRefreshToken);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenStr)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .lastLogin(user.getLastLogin() != null
                                ? user.getLastLogin().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z"
                                : null)
                        .build())
                .build();
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.findByToken(refreshTokenStr)
                .ifPresent(refreshTokenRepository::delete);
    }
}
