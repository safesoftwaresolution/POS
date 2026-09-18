package com.sciencebot.pos.billing.internal.adapters.factus;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.sciencebot.pos.billing.internal.adapters.dto.FactusTokenResponse;

@Component
public class FactusTokenManager {

    private final String factusUrl;
    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;
    private final RestClient restClient;

    private String tokenGuardado;
    private long expiracionTiempoMilis = 0;

    public FactusTokenManager(
            Optional<RestClient.Builder> restClientBuilder,
            @Value("${factus.url:https://api-sandbox.factus.com.co}") String factusUrl,
            @Value("${factus.client-id:}") String clientId,
            @Value("${factus.client-secret:}") String clientSecret,
            @Value("${factus.username:}") String username,
            @Value("${factus.password:}") String password) {

        this.restClient = restClientBuilder
                .map(RestClient.Builder::build)
                .orElse(RestClient.create());
        this.factusUrl = factusUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
    }

    public synchronized String getAccessToken() {
        if (tokenGuardado != null && System.currentTimeMillis() < expiracionTiempoMilis) {
            return tokenGuardado;
        }
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("username", username);
        formData.add("password", password);

        FactusTokenResponse response = restClient.post()
                .uri(factusUrl + "/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(formData)
                .retrieve()
                .body(FactusTokenResponse.class);

        if (response == null) {
            throw new IllegalStateException(
                    "No se pudo obtener el token de autenticación desde Factus API. Respuesta nula o inválida.");
        }

        this.tokenGuardado = response.accessToken();
        this.expiracionTiempoMilis = System.currentTimeMillis() + ((response.expiresIn() - 60) * 1000L);

        return this.tokenGuardado;
    }
}