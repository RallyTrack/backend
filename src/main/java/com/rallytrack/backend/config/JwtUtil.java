package com.rallytrack.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(Long userId, String email) {
        return generateToken(userId, email, accessTokenExpiration, "access");
    }

    public String generateRefreshToken(Long userId, String email) {
        return generateToken(userId, email, refreshTokenExpiration, "refresh");
    }

    private String generateToken(Long userId, String email, long expiration, String tokenType) {
        Date now = new Date();
        return Jwts.builder()
                .claim("token_type", tokenType)
                .claim("user_id", userId)
                .claim("email", email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return parseAccessToken(token).get("user_id", Long.class);
    }

    public Claims parseAccessToken(String token) {
        return parseTypedToken(token, "access");
    }

    private Claims parseTypedToken(String token, String expectedType) {
        Claims claims = parseToken(token);
        Long userId = claims.get("user_id", Long.class);
        if (!expectedType.equals(claims.get("token_type", String.class)) || userId == null || userId <= 0 || claims.getExpiration() == null) {
            throw new IllegalArgumentException("Invalid token purpose or principal");
        }
        return claims;
    }

    public boolean isValidRefreshToken(String token) {
        try {
            parseTypedToken(token, "refresh");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
