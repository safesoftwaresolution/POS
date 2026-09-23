package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.FactusApiException;
import com.sciencebot.pos.billing.internal.adapters.dto.FactusTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Obtiene y guarda en memoria el access_token de Factus de cada local y entorno.
 * Usa siempre las credenciales que el super admin cargó para ese local; no hay credenciales globales.
 */
@Component
public class FactusTokenManager {

    private record CacheKey(Long storeId, BillingEnvironment environment) {}

    private record CachedToken(String token, long expiresAtMillis, String fingerprint) {}

    private static final int DEFAULT_EXPIRES_IN_SECONDS = 3600;
    private static final int SAFETY_MARGIN_SECONDS = 60;

    private final RestClient restClient;
    private final FactusCredentialsResolver resolver;
    private final LongSupplier clock;
    private final Map<CacheKey, CachedToken> tokens = new ConcurrentHashMap<>();
    /** Un candado por local y entorno: un local lento no frena a los demás. */
    private final Map<CacheKey, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public FactusTokenManager(FactusCredentialsResolver resolver) {
        this(RestClient.create(), resolver, System::currentTimeMillis);
    }

    public FactusTokenManager(RestClient restClient, FactusCredentialsResolver resolver, LongSupplier clock) {
        this.restClient = restClient;
        this.resolver = resolver;
        this.clock = clock;
    }

    /** Token del local de la solicitud en curso. */
    public String getAccessToken() {
        return getAccessToken((Long) null);
    }

    /** Token de un local (nulo = el de la solicitud en curso). Exige facturación activa. */
    public String getAccessToken(Long storeId) {
        return getAccessToken(resolver.resolve(storeId));
    }

    public String getAccessToken(FactusAccess access) {
        CacheKey key = new CacheKey(access.storeId(), access.environment());
        String fingerprint = access.fingerprint();

        CachedToken cached = tokens.get(key);
        if (isUsable(cached, fingerprint)) {
            return cached.token();
        }

        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            cached = tokens.get(key);
            if (isUsable(cached, fingerprint)) {
                return cached.token();
            }
            CachedToken fresh = requestToken(access, fingerprint);
            tokens.put(key, fresh);
            return fresh.token();
        }
    }

    /** URL de Factus del entorno del local de la solicitud en curso. */
    public String getBaseUrl() {
        return resolver.resolve(null).baseUrl();
    }

    /** Descarta los tokens guardados de un local (ambos entornos). */
    public void invalidate(Long storeId) {
        if (storeId == null) {
            return;
        }
        for (BillingEnvironment environment : BillingEnvironment.values()) {
            tokens.remove(new CacheKey(storeId, environment));
        }
    }

    /**
     * Pide un token nuevo con las credenciales guardadas del local para su entorno activo,
     * aunque todavía no esté {@code ACTIVE}. Lanza {@link FactusApiException} si Factus las rechaza.
     */
    public void verifyConnection(Long storeId) {
        verifyConnection(resolver.resolveIgnoringStatus(storeId));
    }

    /**
     * Igual que {@link #verifyConnection(Long)} pero prueba un entorno explícito, sea o no el
     * entorno activo del local (para revisar producción antes de cambiarse a ella).
     */
    public void verifyConnection(Long storeId, BillingEnvironment environment) {
        verifyConnection(resolver.resolveIgnoringStatus(storeId, environment));
    }

    private void verifyConnection(FactusAccess access) {
        CacheKey key = new CacheKey(access.storeId(), access.environment());
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            tokens.put(key, requestToken(access, access.fingerprint()));
        }
    }

    private boolean isUsable(CachedToken cached, String fingerprint) {
        return cached != null
                && cached.fingerprint().equals(fingerprint)
                && clock.getAsLong() < cached.expiresAtMillis();
    }

    private CachedToken requestToken(FactusAccess access, String fingerprint) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", access.clientId());
        form.add("client_secret", access.clientSecret());
        form.add("username", access.username());
        form.add("password", access.password());

        FactusTokenResponse response;
        try {
            response = restClient.post()
                    .uri(access.baseUrl() + "/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(FactusTokenResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            throw new FactusApiException(status,
                    "Factus no aceptó las credenciales del local en " + access.environment()
                            + " (HTTP " + status + ")", ex);
        } catch (ResourceAccessException ex) {
            throw new FactusApiException(0, "No se pudo conectar con Factus: " + ex.getMessage(), ex);
        }

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new FactusApiException(0, "Factus respondió sin un token de acceso válido", null);
        }

        int expiresIn = response.expiresIn() != null ? response.expiresIn() : DEFAULT_EXPIRES_IN_SECONDS;
        long expiresAt = clock.getAsLong() + Math.max(0, expiresIn - SAFETY_MARGIN_SECONDS) * 1000L;
        return new CachedToken(response.accessToken(), expiresAt, fingerprint);
    }
}
