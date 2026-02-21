package com.rallytrack.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    // 인증이 필요 없는 경로
    private static final List<String> WHITELIST = List.of(
            "/api/v1/onboarding",
            "/api/v1/login",
            "/api/v1/signup",
            "/swagger-ui",
            "/v3/api-docs",
            "/api/v1/analysis/complete"
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
                // X-User-Id 헤더 대신 JWT에서 추출한 userId를 attribute로 저장
                request.setAttribute("userId", userId);
                filterChain.doFilter(request, response);
                return;
            }
        }

        // X-User-Id 헤더가 있으면 허용 (Swagger 테스트용)
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 인증 실패
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"인증이 필요합니다.\"}");
    }
}
