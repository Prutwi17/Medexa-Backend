package com.medexa.auth.repository;

import com.medexa.auth.entity.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE PasswordResetToken t
               SET t.usedAt = :usedAt
             WHERE t.user.id = :userId
               AND t.usedAt IS NULL
            """)
    int invalidateActiveTokensForUser(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);
}
