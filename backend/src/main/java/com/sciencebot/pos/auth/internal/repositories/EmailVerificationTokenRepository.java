package com.sciencebot.pos.auth.internal.repositories;

import com.sciencebot.pos.auth.internal.entities.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);
}
