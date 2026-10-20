package com.sciencebot.pos.auth.internal.repositories;

import com.sciencebot.pos.auth.internal.entities.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Bloqueo pesimista para serializar rotaciones concurrentes del mismo token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revoked = true, t.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE t.familyId = :familyId AND t.revoked = false")
    void revokeFamily(@Param("familyId") String familyId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.userId = :userId AND (t.revoked = true OR t.expiresAt < :now)")
    void purgeInactiveForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
