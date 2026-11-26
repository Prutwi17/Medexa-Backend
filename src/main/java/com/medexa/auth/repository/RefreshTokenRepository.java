package com.medexa.auth.repository;

import com.medexa.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
               SET rt.revokedAt = :revokedAt,
                   rt.revocationReason = :reason
             WHERE rt.tokenFamilyId = :familyId
               AND rt.revokedAt IS NULL
            """)
    int revokeFamily(
            @Param("familyId") String familyId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason);

    @Modifying
    @Query("""
            UPDATE RefreshToken rt
               SET rt.revokedAt = :revokedAt,
                   rt.revocationReason = :reason
             WHERE rt.user.id = :userId
               AND rt.revokedAt IS NULL
            """)
    int revokeAllForUser(
            @Param("userId") Long userId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason);
}
