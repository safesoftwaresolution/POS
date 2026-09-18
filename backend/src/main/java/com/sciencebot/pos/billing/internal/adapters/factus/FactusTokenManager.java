package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.config.TenantContext;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.settings.StoreBillingConfigDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FactusTokenManager {

    private static final Logger log = LoggerFactory.getLogger(FactusTokenManager.class);

    private record CachedToken(String token, long expiresAtMillis) {}

    @Value("${factus.url:https://api-sandbox.factus.com.co}")
    private String factusUrl;

    @Value("${factus.client-id:}")
    private String clientId;

    @Value("${factus.client-secret:}")
    private String clientSecret;

    @Value("${factus.username:}")
    private String username;

    @Value("${factus.password:}")
    private String password;

    private final RestClient restClient;
    private final SettingsFacade settingsFacade;
    private final Map<Long, CachedToken> storeTokens = new ConcurrentHashMap<>();

    @Autowired
    public FactusTokenManager(@Lazy SettingsFacade settingsFacade) {
        this.restClient = RestClient.create();
        this.settingsFacade = settingsFacade;
    }

    public FactusTokenManager() {
        this.restClient = RestClient.create();
        this.settingsFacade = null;
    }

    // Constructor para testing
    public FactusTokenManager(RestClient restClient, String factusUrl, String clientId, String clientSecret, String username, String password) {
        this.restClient = restClient;
        this.factusUrl = factusUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
        this.settingsFacade = null;
    }

    /**
     * Obtiene un access_token para la tienda del contexto actual o global.
     */
    public String getAccessToken() {
        return getAccessToken(TenantContext.getStoreId());
    }

    /**
     * Obtiene un access_token válido para la API de Factus específico de la tienda.
     */
    public synchronized String getAccessToken(Long storeId) {
        Long cacheKey = storeId != null ? storeId : 0L;

        CachedToken cached = storeTokens.get(cacheKey);
        if (cached != null && System.currentTimeMillis() < cached.expiresAtMillis()) {
            return cached.token();
        }

        String targetClientId = this.clientId;
        String targetClientSecret = this.clientSecret;
        String targetUsername = this.username;
        String targetPassword = this.password;

        if (storeId != null && settingsFacade != null) {
            Optional<StoreBillingConfigDto> configOpt = settingsFacade.getBillingConfig(storeId);
            if (configOpt.isPresent()) {
                StoreBillingConfigDto cfg = configOpt.get();
                if (cfg.factusClientId() != null && !cfg.factusClientId().isBlank()
                        && cfg.factusUsername() != null && !cfg.factusUsername().isBlank()) {
                    targetClientId = cfg.factusClientId();
                    targetClientSecret = cfg.factusClientSecret() != null ? cfg.factusClientSecret() : "";
                    targetUsername = cfg.factusUsername();
                    targetPassword = cfg.factusPassword() != null ? cfg.factusPassword() : "";
                }
            }
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", targetClientId != null ? targetClientId : "");
        formData.add("client_secret", targetClientSecret != null ? targetClientSecret : "");
        formData.add("username", targetUsername != null ? targetUsername : "");
        formData.add("password", targetPassword != null ? targetPassword : "");

        String targetUrl = (factusUrl != null && !factusUrl.isBlank())
                ? factusUrl
                : "https://api-sandbox.factus.com.co";

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(targetUrl + "/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(formData)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new IllegalStateException("No se pudo obtener el token de autenticación desde Factus API. Respuesta nula o inválida.");
        }

        String token = (String) response.get("access_token");
        Object expiresInRaw = response.getOrDefault("expires_in", 3600);
        int expiresInSeconds = (expiresInRaw instanceof Number n) ? n.intValue() : 3600;
        long expiresAtMillis = System.currentTimeMillis() + ((expiresInSeconds - 60) * 1000L);

        storeTokens.put(cacheKey, new CachedToken(token, expiresAtMillis));
        return token;
    }
}
