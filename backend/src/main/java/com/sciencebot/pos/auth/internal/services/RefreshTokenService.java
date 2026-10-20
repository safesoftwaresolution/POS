package com.sciencebot.pos.auth.internal.services;

import com.sciencebot.pos.auth.internal.entities.RefreshToken;
import com.sciencebot.pos.auth.internal.repositories.RefreshTokenRepository;
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
import java.util.UUID;

/**
 * Gestiona el ciclo de vida de los refresh tokens: emision, rotacion y revocacion.
 * El token que sale al cliente es un valor opaco aleatorio; en base de datos solo se
 * guarda su hash SHA-256, de modo que una fuga de la tabla no permite reconstruir tokens.
 *
 * <p>Cada sesion se identifica con un {@code familyId} que se hereda en cada rotacion.
 * Si se presenta un token ya rotado (revocado), se asume robo y se revoca la familia
 * completa (deteccion de reuso, RFC 6819).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${security.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    /**
     * Ventana de gracia tras rotar un token. Si el token ya rotado se vuelve a presentar
     * dentro de esta ventana, se asume un reintento de red benigno (la respuesta con el
     * token nuevo se perdió) y NO se revoca la familia: así un reintento no cierra la sesion.
     * Pasada la ventana, un token revocado presentado de nuevo se trata como reuso (robo).
     */
    @Value("${security.jwt.refresh-reuse-grace-ms:15000}")
    private long reuseGraceMs;

    public RefreshTokenService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    /** Emite el primer refresh token de una nueva sesion (nueva familia). */
    @Transactional
    public String issue(Long userId) {
        // Mantiene acotada la tabla: elimina tokens caducados/revocados del usuario.
        repository.purgeInactiveForUser(userId, LocalDateTime.now());

        String rawToken = generateRawToken();
        RefreshToken entity = newToken(rawToken, userId, UUID.randomUUID().toString());
        repository.save(entity);
        return rawToken;
    }

    /**
     * Rota un refresh token opaco de forma atomica (bloqueo pesimista):
     * revoca el actual y emite uno nuevo dentro de la misma familia.
     * Lanza {@link BadCredentialsException} si es invalido, expirado o reutilizado.
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Refresh token requerido");
        }

        RefreshToken current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Refresh token invalido"));

        if (current.isRevoked()) {
            // Token ya rotado presentado de nuevo. Dentro de la ventana de gracia se asume un
            // reintento de red benigno (no se revoca la familia); fuera de ella, robo probable.
            if (isWithinReuseGrace(current)) {
                throw new BadCredentialsException("Refresh token ya rotado; reintente con el token vigente");
            }
            repository.revokeFamily(current.getFamilyId());
            throw new BadCredentialsException("Refresh token reutilizado; sesion revocada por seguridad");
        }
        if (current.isExpired()) {
            throw new BadCredentialsException("Refresh token expirado");
        }

        current.setRevoked(true);
        repository.save(current);

        String newRawToken = generateRawToken();
        repository.save(newToken(newRawToken, current.getUserId(), current.getFamilyId()));

        return new RotationResult(newRawToken, current.getUserId(), current.getFamilyId());
    }

    /** Cierra la sesion: revoca la familia completa del token presentado (si existe). */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId()));
    }

    /** Revoca todos los tokens activos de una familia (sesion). */
    @Transactional
    public void revokeFamily(String familyId) {
        if (familyId != null) {
            repository.revokeFamily(familyId);
        }
    }

    /**
     * True si el token fue rotado (revocado) hace menos de {@code reuseGraceMs}. Usa
     * {@code updatedAt}, que se fija en el instante de la rotacion. Con la ventana en 0
     * el comportamiento vuelve a ser deteccion de reuso estricta.
     */
    private boolean isWithinReuseGrace(RefreshToken token) {
        if (reuseGraceMs <= 0 || token.getUpdatedAt() == null) {
            return false;
        }
        LocalDateTime threshold = LocalDateTime.now().minus(Duration.ofMillis(reuseGraceMs));
        return token.getUpdatedAt().isAfter(threshold);
    }

    private RefreshToken newToken(String rawToken, Long userId, String familyId) {
        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(hash(rawToken));
        entity.setUserId(userId);
        entity.setFamilyId(familyId);
        entity.setExpiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshExpirationMs)));
        entity.setRevoked(false);
        return entity;
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

    /** Resultado de una rotacion: nuevo token opaco + a quien pertenece. */
    public record RotationResult(String newRawToken, Long userId, String familyId) {}
}
