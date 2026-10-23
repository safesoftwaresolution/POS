package com.sciencebot.pos.auth.internal.services;

import com.sciencebot.pos.auth.internal.entities.PasswordResetToken;
import com.sciencebot.pos.auth.internal.repositories.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Value;
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
 * Emite y consume tokens de recuperacion de contrasena: opacos, de un solo uso y de vigencia corta.
 * Mismo esquema que {@link EmailVerificationService}. Los fallos de token se lanzan como
 * {@link IllegalArgumentException} (HTTP 400) para que el usuario vea el motivo; un 401 el cliente
 * lo interpretaria como sesion expirada.
 */
@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${security.password-reset.expiration-ms:3600000}")
    private long expirationMs;

    public PasswordResetService(PasswordResetTokenRepository repository) {
        this.repository = repository;
    }

    /** Invalida cualquier token pendiente del usuario y emite uno nuevo. */
    @Transactional
    public String issue(Long userId) {
        repository.deleteByUserId(userId);

        String rawToken = generateRawToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hash(rawToken));
        token.setUserId(userId);
        token.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(expirationMs)));
        repository.save(token);
        return rawToken;
    }

    /** Borra los tokens del usuario (previo a eliminar su cuenta). */
    @Transactional
    public void deleteForUser(Long userId) {
        repository.deleteByUserId(userId);
    }

    /** Valida y consume el token, devolviendo el id del usuario asociado. */
    @Transactional
    public Long consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token de recuperacion requerido");
        }

        PasswordResetToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("El enlace de recuperacion no es valido"));

        if (token.isUsed()) {
            throw new IllegalArgumentException("El enlace de recuperacion ya fue utilizado");
        }
        if (token.isExpired()) {
            throw new IllegalArgumentException("El enlace de recuperacion expiro. Solicita uno nuevo");
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
