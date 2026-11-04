package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lee la empresa del local en Alegra ({@code GET /companies/{id}}) con una caché en memoria por local y entorno
 * (TTL corto) para no consultar al proveedor en cada emisión. {@link #fetch} siempre consulta (sirve para probar
 * la conexión) y {@link #invalidate} descarta lo guardado cuando cambia la cuenta del local.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraCompanyProvider {

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope(AlegraCompany company) {}

    private record Cached(AlegraCompany company, Instant expiresAt) {}

    private final AlegraClientHelper helper;
    private final Duration ttl;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public AlegraCompanyProvider(AlegraClientHelper helper,
                                 @Value("${alegra.company-cache-ttl:PT10M}") Duration ttl) {
        this(helper, ttl, Clock.systemUTC());
    }

    public AlegraCompanyProvider(AlegraClientHelper helper, Duration ttl, Clock clock) {
        this.helper = helper;
        this.ttl = ttl;
        this.clock = clock;
    }

    /** La empresa del local, de la caché si sigue vigente. */
    public AlegraCompany get(AlegraAccess access) {
        Cached cached = cache.get(key(access));
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            return cached.company();
        }
        return fetch(access);
    }

    /** Consulta siempre a Alegra y actualiza la caché. */
    public AlegraCompany fetch(AlegraAccess access) {
        try {
            Envelope envelope = helper.authorizedGet("/companies/" + access.companyId(), access)
                    .retrieve().body(Envelope.class);
            if (envelope == null || envelope.company() == null) {
                throw new AlegraApiException(0, "Alegra devolvió una respuesta vacía al consultar la empresa", null);
            }
            cache.put(key(access), new Cached(envelope.company(), clock.instant().plus(ttl)));
            return envelope.company();
        } catch (Exception ex) {
            throw helper.translate("No se pudo leer la empresa en Alegra", ex);
        }
    }

    /** Descarta lo guardado de un local (cualquier entorno). */
    public void invalidate(Long storeId) {
        String prefix = storeId + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static String key(AlegraAccess access) {
        return access.storeId() + ":" + access.environment();
    }
}
