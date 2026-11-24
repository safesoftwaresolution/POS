package com.sciencebot.pos.voicesales.internal.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Component
@ConditionalOnProperty(name = "voice-sales.enabled", havingValue = "true")
final class VoicePreviewQuota {
    private static final int MAX_KEYS = 10_000;
    private record Actor(long store, String username) {}
    private final Map<Long, Integer> stores = new HashMap<>();
    private final Map<Actor, Integer> actors = new HashMap<>();
    private final int userLimit;
    private final int storeLimit;
    private final Clock clock;
    private long window = Long.MIN_VALUE;

    @Autowired
    VoicePreviewQuota(@Value("${voice-sales.user-per-minute:6}") int userLimit,
                      @Value("${voice-sales.store-per-minute:30}") int storeLimit) {
        this(userLimit, storeLimit, Clock.systemUTC());
    }

    VoicePreviewQuota(int userLimit, int storeLimit, Clock clock) {
        if (userLimit < 1 || storeLimit < 1) throw new IllegalArgumentException("Voice quotas must be positive");
        this.userLimit = userLimit;
        this.storeLimit = storeLimit;
        this.clock = clock;
    }

    /** Both buckets are checked and consumed together, only once per admitted preview. */
    synchronized void acquire(long store, String username) {
        long minute = Math.floorDiv(clock.millis(), 60_000);
        if (minute > window) {
            stores.clear();
            actors.clear();
            window = minute;
        }
        Actor actor = new Actor(store, username);
        int userCount = actors.getOrDefault(actor, 0);
        int storeCount = stores.getOrDefault(store, 0);
        if (userCount >= userLimit || storeCount >= storeLimit || (userCount == 0 && actors.size() >= MAX_KEYS)) {
            throw new PreviewRejected(TOO_MANY_REQUESTS, "Se alcanzó el límite de dictados. Intenta en el próximo minuto.");
        }
        actors.put(actor, userCount + 1);
        stores.put(store, storeCount + 1);
    }
}
