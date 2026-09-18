package com.sciencebot.pos.billing.internal.adapters.factus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Componente utilitario para comunicación HTTP autorizada con la API de Factus.
 */
@Component
public class FactusClientHelper {

    private final FactusTokenManager tokenManager;
    private final RestClient restClient;

    @Value("${factus.url:https://api-sandbox.factus.com.co}")
    private String factusUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public FactusClientHelper(FactusTokenManager tokenManager) {
        this(tokenManager, RestClient.create(), null);
    }

    public FactusClientHelper(FactusTokenManager tokenManager, RestClient restClient, String factusUrl) {
        this.tokenManager = tokenManager;
        this.restClient = restClient != null ? restClient : RestClient.create();
        this.factusUrl = factusUrl;
    }

    public String buildUrl(String path) {
        String base = (factusUrl != null && !factusUrl.isBlank())
                ? factusUrl
                : "https://api-sandbox.factus.com.co";
        return base + path;
    }

    public RestClient.RequestBodySpec authorizedPost(String path) {
        return authorizedPost(path, null);
    }

    public RestClient.RequestBodySpec authorizedPost(String path, Long storeId) {
        return restClient.post()
                .uri(buildUrl(path))
                .header("Authorization", "Bearer " + tokenManager.getAccessToken(storeId));
    }

    public RestClient.RequestHeadersSpec<?> authorizedGet(String path) {
        return authorizedGet(path, null);
    }

    public RestClient.RequestHeadersSpec<?> authorizedGet(String path, Long storeId) {
        return restClient.get()
                .uri(buildUrl(path))
                .header("Authorization", "Bearer " + tokenManager.getAccessToken(storeId));
    }

    public RestClient.RequestHeadersSpec<?> authorizedDelete(String path) {
        return authorizedDelete(path, null);
    }

    public RestClient.RequestHeadersSpec<?> authorizedDelete(String path, Long storeId) {
        return restClient.delete()
                .uri(buildUrl(path))
                .header("Authorization", "Bearer " + tokenManager.getAccessToken(storeId));
    }

    public RestClient.RequestBodySpec authorizedPatch(String path) {
        return authorizedPatch(path, null);
    }

    public RestClient.RequestBodySpec authorizedPatch(String path, Long storeId) {
        return restClient.patch()
                .uri(buildUrl(path))
                .header("Authorization", "Bearer " + tokenManager.getAccessToken(storeId));
    }

    public String truncateError(String message) {
        if (message == null) return null;
        return message.length() > 1000 ? message.substring(0, 997) + "..." : message;
    }
}
