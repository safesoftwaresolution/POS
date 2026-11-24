package com.sciencebot.pos.voicesales.internal.services;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoicePreviewQuotaTest {
    @Test
    void userAndStoreLimitsAreAtomicAndExpireTogether() {
        Clock clock = mock(Clock.class);
        var quota = new VoicePreviewQuota(1, 2, clock);
        quota.acquire(1L, "one");
        assertThatThrownBy(() -> quota.acquire(1L, "one")).isInstanceOf(PreviewRejected.class);
        quota.acquire(1L, "two"); // Rejected attempts do not consume another store token.
        assertThatThrownBy(() -> quota.acquire(1L, "three")).isInstanceOf(PreviewRejected.class);
        quota.acquire(2L, "one");
        when(clock.millis()).thenReturn(60_000L);
        quota.acquire(1L, "one");
    }

    @Test
    void parallelCashiersCannotExceedStoreLimit() {
        var quota = new VoicePreviewQuota(10, 4, Clock.fixed(java.time.Instant.EPOCH, java.time.ZoneOffset.UTC));
        long admitted = IntStream.range(0, 32).parallel().filter(i -> {
            try { quota.acquire(2L, "cashier" + i); return true; }
            catch (PreviewRejected denied) { return false; }
        }).count();
        assertThat(admitted).isEqualTo(4);
    }
}
