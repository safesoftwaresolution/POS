package com.sciencebot.pos.auth;

import com.sciencebot.pos.auth.internal.entities.EmailVerificationToken;
import com.sciencebot.pos.auth.internal.repositories.EmailVerificationTokenRepository;
import com.sciencebot.pos.auth.internal.services.EmailVerificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {

    private final EmailVerificationTokenRepository repository = mock(EmailVerificationTokenRepository.class);
    private final EmailVerificationService service = new EmailVerificationService(repository);

    private void givenToken(LocalDateTime expiresAt, LocalDateTime usedAt) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(3L);
        token.setExpiresAt(expiresAt);
        token.setUsedAt(usedAt);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
    }

    @Test
    void consume_MissingOrUnknownToken_IsAnInvalidLinkWithAClearMessage() {
        InvalidVerificationTokenException missing = assertThrows(InvalidVerificationTokenException.class, () -> service.consume(" "));
        assertEquals("El enlace de verificación no es válido. Revisa que lo hayas copiado completo.", missing.getMessage());

        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        InvalidVerificationTokenException unknown = assertThrows(InvalidVerificationTokenException.class, () -> service.consume("raw"));
        assertEquals("El enlace de verificación no es válido o fue reemplazado por uno más reciente. Pide un enlace nuevo.", unknown.getMessage());
    }

    @Test
    void consume_UsedToken_SaysItWasAlreadyUsed() {
        givenToken(LocalDateTime.now().plusHours(1), LocalDateTime.now().minusMinutes(5));

        InvalidVerificationTokenException ex = assertThrows(InvalidVerificationTokenException.class, () -> service.consume("raw"));

        assertEquals("Este enlace de verificación ya fue utilizado. Si ya verificaste tu correo, inicia sesión.", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void consume_ExpiredToken_SaysItExpired() {
        givenToken(LocalDateTime.now().minusMinutes(1), null);

        InvalidVerificationTokenException ex = assertThrows(InvalidVerificationTokenException.class, () -> service.consume("raw"));

        assertEquals("El enlace de verificación venció. Pide un enlace nuevo.", ex.getMessage());
    }

    @Test
    void consume_ValidToken_MarksItUsedAndReturnsTheUser() {
        givenToken(LocalDateTime.now().plusHours(1), null);

        assertEquals(3L, service.consume("raw"));
        verify(repository).save(argThat(t -> t.getUsedAt() != null));
    }
}
