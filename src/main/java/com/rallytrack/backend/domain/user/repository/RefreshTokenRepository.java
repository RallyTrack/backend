package com.rallytrack.backend.domain.user.repository;

import com.rallytrack.backend.domain.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByToken(String token);
    void deleteByUserId(Long userId);
    
    // ✅ 추가: 안전한 삭제를 위한 네이티브 쿼리 (권장 방법)
    // 동시 삭제 시도 시에도 예외를 발생시키지 않습니다.
    @Modifyingg
    @Query(value = "DELETE FROM refresh_tokens WHERE user_id = :userId", nativeQuery = true)
    void deleteByUserIdSafely(@Param("userId") Long userId);
}