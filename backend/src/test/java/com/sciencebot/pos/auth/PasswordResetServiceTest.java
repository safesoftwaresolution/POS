package com.sciencebot.pos.auth;

import com.sciencebot.pos.auth.internal.entities.PasswordResetToken;
import com.sciencebot.pos.auth.internal.repositories.PasswordResetTokenRepository;
import com.sciencebot.pos.auth.internal.services.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private PasswordResetTokenRepository repository;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        repository = mock(PasswordResetTokenRepository.class);
        service = new PasswordResetService(repository);
        ReflectionTestUtils.setField(service, "expirationMs", 3_600_000L);
    }

    @Test
    void issue_StoresOnlyTheHashAndReplacesPendingTokens() {
        String raw = service.issue(5L);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(repository).deleteByUserId(5L);
        verify(repository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertEquals(5L, saved.getUserId());
        assertEquals(64, saved.getTokenHash().length());
        assertNotEquals(raw, saved.getTokenHash());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void consume_ValidToken_MarksUsedAndReturnsUser() {
        String raw = service.issue(5L);
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(repository).save(captor.capture());
        PasswordResetToken stored = captor.getValue();
        when(repository.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        assertEquals(5L, service.consume(raw));
        assertTrue(stored.isUsed());
    }

    @Test
    void consume_UsedExpiredUnknownOrBlankToken_IsRejectedWithBadRequestException() {
        String raw = service.issue(5L);
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(repository).save(captor.capture());
        PasswordResetToken stored = captor.getValue();
        when(repository.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        stored.setUsedAt(LocalDateTime.now());
        assertThrows(IllegalArgumentException.class, () -> service.consume(raw));

        stored.setUsedAt(null);
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> service.consume(raw));

        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.consume("otro"));
        assertThrows(IllegalArgumentException.class, () -> service.consume(" "));
    }
}
