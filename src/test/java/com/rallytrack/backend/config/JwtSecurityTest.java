package com.rallytrack.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class JwtSecurityTest {
    private final JwtUtil jwt = new JwtUtil("test-only-signing-secret-long-enough-for-hs256", 60000, 3600000);

    private MockHttpServletResponse request(String token, AtomicBoolean called) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/videos/1");
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        new JwtAuthFilter(jwt).doFilter(request, response, (q, s) -> called.set(true));
        return response;
    }

    @Test void accessTokenIsAccepted() throws Exception {
        var called = new AtomicBoolean();
        assertEquals(200, request(jwt.generateAccessToken(1L, "test@example.invalid"), called).getStatus());
        assertTrue(called.get());
    }

    @Test void refreshCannotAuthorizeApi() throws Exception {
        var called = new AtomicBoolean();
        assertEquals(401, request(jwt.generateRefreshToken(1L, "test@example.invalid"), called).getStatus());
        assertFalse(called.get());
    }

    @Test void missingAndMalformedTokensAreRejected() throws Exception {
        for (String token : new String[]{null, "invalid.token"}) {
            var called = new AtomicBoolean();
            assertEquals(401, request(token, called).getStatus());
            assertFalse(called.get());
        }
    }

    @Test void signedButUntypedExpiredOrInvalidPrincipalTokensAreRejected() throws Exception {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor("test-only-signing-secret-long-enough-for-hs256".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String type : java.util.List.of("", "unknown", "refresh", "access")) {
            var builder = io.jsonwebtoken.Jwts.builder().claim("user_id", type.equals("access") ? -1 : 1)
                    .expiration(new java.util.Date(System.currentTimeMillis()+60000));
            if (!type.isEmpty()) builder.claim("token_type",type);
            var called = new java.util.concurrent.atomic.AtomicBoolean();
            org.assertj.core.api.Assertions.assertThat(request(builder.signWith(key).compact(),called).getStatus()).isEqualTo(401);
            org.assertj.core.api.Assertions.assertThat(called.get()).isFalse();
        }
        String expired = io.jsonwebtoken.Jwts.builder().claim("user_id",1).claim("token_type","access")
                .expiration(new java.util.Date(0)).signWith(key).compact();
        org.assertj.core.api.Assertions.assertThat(request(expired,new java.util.concurrent.atomic.AtomicBoolean()).getStatus()).isEqualTo(401);
    }
}
