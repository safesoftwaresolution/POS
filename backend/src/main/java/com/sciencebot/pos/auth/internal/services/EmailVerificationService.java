package com.sciencebot.pos.auth.internal.services;

import com.sciencebot.pos.auth.internal.entities.EmailVerificationToken;
import com.sciencebot.pos.auth.internal.repositories.EmailVerificationTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Emite y consume tokens de verificacion de correo: opacos, de un solo uso y con expiracion.
 * Mismo esquema de hash SHA-256 que {@link RefreshTokenService}, sin rotacion (no aplica aqui).
 */
@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${security.email-verification.expiration-ms:86400000}")
    private long expirationMs;

    public EmailVerificationService(EmailVerificationTokenRepository repository) {
        this.repository = repository;
    }

    /** Invalida cualquier token pendiente del usuario y emite uno nuevo. */
    @Transactional
    public String issue(Long userId) {
        repository.deleteByUserId(userId);

        String rawToken = generateRawToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setTokenHash(hash(rawToken));
        token.setUserId(userId);
        token.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(expirationMs)));
        repository.save(token);
        return rawToken;
    }

    /** Valida y consume el token, devolviendo el id del usuario asociado. */
    @Transactional
    public Long consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Token de verificacion requerido");
        }

        EmailVerificationToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Token de verificacion invalido"));

        if (token.isUsed()) {
            throw new BadCredentialsException("El token de verificacion ya fue utilizado");
        }
        if (token.isExpired()) {
            throw new BadCredentialsException("El token de verificacion expiro");
        }

        token.setUsedAt(LocalDateTime.now());
        repository.save(token);
        return token.getUserId();
    }

    private String generateRawToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
