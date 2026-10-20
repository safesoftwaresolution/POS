package com.sciencebot.pos.auth;

import com.sciencebot.pos.auth.internal.services.RefreshTokenService;
import com.sciencebot.pos.users.UserFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserFacade userFacade;

    private Long userId;

    @BeforeEach
    void setUp() {
        // 'admin' es sembrado por DatabaseSeeder al arrancar el contexto.
        userId = userFacade.findByUsername("admin").orElseThrow().id();
    }

    @Test
    void rotate_ReturnsNewTokenForSameUser() {
        String raw = refreshTokenService.issue(userId);

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(raw);

        assertNotNull(result.newRawToken());
        assertNotEquals(raw, result.newRawToken());
        assertEquals(userId, result.userId());
    }

    @Test
    void rotate_ReusedTokenAfterGrace_DetectsReuseAndRevokesFamily() {
        // Ventana de gracia en 0 => deteccion de reuso estricta (comportamiento de robo).
        ReflectionTestUtils.setField(refreshTokenService, "reuseGraceMs", 0L);

        String raw = refreshTokenService.issue(userId);
        RefreshTokenService.RotationResult first = refreshTokenService.rotate(raw);

        // Reusar el token ya rotado => se detecta robo.
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotate(raw));

        // Y la familia completa queda revocada: el token nuevo tampoco sirve.
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotate(first.newRawToken()));
    }

    @Test
    void rotate_ReusedTokenWithinGrace_RejectsButKeepsSessionAlive() {
        // Ventana de gracia amplia => un reintento del token ya rotado no mata la sesion.
        ReflectionTestUtils.setField(refreshTokenService, "reuseGraceMs", 60_000L);

        String raw = refreshTokenService.issue(userId);
        RefreshTokenService.RotationResult first = refreshTokenService.rotate(raw);

        // Reusar el token viejo dentro de la ventana se rechaza...
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotate(raw));

        // ...pero la familia NO se revoca: el token vigente sigue funcionando.
        RefreshTokenService.RotationResult second = refreshTokenService.rotate(first.newRawToken());
        assertNotNull(second.newRawToken());
        assertNotEquals(first.newRawToken(), second.newRawToken());
    }

    @Test
    void rotate_InvalidToken_Throws() {
        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotate("no-existe"));
    }

    @Test
    void revoke_MakesTokenUnusable() {
        String raw = refreshTokenService.issue(userId);

        refreshTokenService.revoke(raw);

        assertThrows(BadCredentialsException.class, () -> refreshTokenService.rotate(raw));
    }
}
