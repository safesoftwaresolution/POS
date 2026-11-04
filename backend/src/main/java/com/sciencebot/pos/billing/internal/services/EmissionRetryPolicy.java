package com.sciencebot.pos.billing.internal.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/** Espera creciente entre reintentos de emisión: 2, 4, 8... minutos con tope de 1 hora y hasta 10% de jitter. */
public final class EmissionRetryPolicy {

    private static final long MAX_MINUTES = 60;
    private static final int MAX_EXPONENT = 6;

    private EmissionRetryPolicy() {}

    /** Espera base tras {@code attempts} intentos fallidos: {@code 2^attempts} minutos, máximo una hora. */
    public static Duration baseDelay(int attempts) {
        int exponent = Math.min(Math.max(attempts, 1), MAX_EXPONENT);
        return Duration.ofMinutes(Math.min(1L << exponent, MAX_MINUTES));
    }

    /** Cuándo reintentar: la espera base más un jitter de hasta 10% para no sincronizar a varias instancias. */
    public static LocalDateTime nextRetryAt(int attempts, LocalDateTime now) {
        Duration base = baseDelay(attempts);
        long jitterMillis = ThreadLocalRandom.current().nextLong(base.toMillis() / 10 + 1);
        return now.plus(base).plus(Duration.ofMillis(jitterMillis));
    }
}
