package com.rallytrack.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    // AI 서버 콜백 검증용 공유 시크릿 (env: ANALYSIS_CALLBACK_SECRET)
    @Value("${app.analysis.callback-secret}")
    private String callbackSecret;

    // 인증이 필요 없는 경로
    private static final List<String> WHITELIST = List.of(
            "/api/v1/onboarding",
            "/api/v1/login",
            "/api/v1/signup",
            "/api/v1/token/refresh",
            "/api/v1/logout",
            "/swagger-ui",
            "/v3/api-docs"
    );

    // AI 서버 콜백 경로: JWT 대신 X-Internal-Token 헤더로 검증
    private static final List<String> CALLBACK_PATHS = List.of(
            "/api/v1/analysis/complete",
            "/api/v1/analysis/fail"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // CORS Preflight 요청은 인증 없이 통과
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // AI 서버 콜백은 공유 시크릿으로 검증 (도메인이 외부에 노출되므로 무인증 금지)
        if (CALLBACK_PATHS.stream().anyMatch(path::startsWith)) {
            String internalToken = request.getHeader("X-Internal-Token");
            if (internalToken != null && internalToken.equals(callbackSecret)) {
                filterChain.doFilter(request, response);
            } else {
                reject(response);
            }
            return;
        }

        // 화이트리스트 경로는 통과
        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Authorization 헤더에서 JWT 토큰 추출
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.isValid(token)) {
                Long userId = jwtUtil.getUserId(token);
                request.setAttribute("userId", userId);
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 인증 실패
        reject(response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"인증이 필요합니다.\"}");
    }
}
