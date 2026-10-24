package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.exception.FactusApiException;
import com.sciencebot.pos.config.http.OutboundHttp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Componente utilitario para comunicación HTTP autorizada con la API de Factus.
 * Cada llamada usa las credenciales y la URL del local indicado; si no se indica, las del local
 * de la solicitud en curso. No existe una cuenta global a la cual recurrir.
 */
@Component
public class FactusClientHelper {

    private static final int MAX_ERROR_BODY = 500;

    private final FactusTokenManager tokenManager;
    private final FactusCredentialsResolver resolver;
    private final RestClient restClient;

    @Autowired
    public FactusClientHelper(FactusTokenManager tokenManager, FactusCredentialsResolver resolver) {
        this(tokenManager, resolver, OutboundHttp.restClient());
    }

    public FactusClientHelper(FactusTokenManager tokenManager, FactusCredentialsResolver resolver, RestClient restClient) {
        this.tokenManager = tokenManager;
        this.resolver = resolver;
        this.restClient = restClient != null ? restClient : OutboundHttp.restClient();
    }

    /** Credenciales, URL y rango del local (nulo = el de la solicitud en curso). Exige facturación activa. */
    public FactusAccess resolveAccess(Long storeId) {
        return resolver.resolve(storeId);
    }

    /** Descarta el token guardado del local; se usa cuando Factus responde 401. */
    public void invalidateToken(Long storeId) {
        tokenManager.invalidate(storeId != null ? storeId : com.sciencebot.pos.config.tenant.TenantContext.getStoreId());
    }

    public RestClient.RequestBodySpec authorizedPost(String path) {
        return authorizedPost(path, (Long) null);
    }

    public RestClient.RequestBodySpec authorizedPost(String path, Long storeId) {
        return authorizedPost(path, resolveAccess(storeId));
    }

    public RestClient.RequestBodySpec authorizedPost(String path, FactusAccess access) {
        return restClient.post().uri(access.baseUrl() + path).header("Authorization", bearer(access));
    }

    public RestClient.RequestHeadersSpec<?> authorizedGet(String path) {
        return authorizedGet(path, (Long) null);
    }

    public RestClient.RequestHeadersSpec<?> authorizedGet(String path, Long storeId) {
        return authorizedGet(path, resolveAccess(storeId));
    }

    public RestClient.RequestHeadersSpec<?> authorizedGet(String path, FactusAccess access) {
        return restClient.get().uri(access.baseUrl() + path).header("Authorization", bearer(access));
    }

    public RestClient.RequestHeadersSpec<?> authorizedDelete(String path) {
        return authorizedDelete(path, (Long) null);
    }

    public RestClient.RequestHeadersSpec<?> authorizedDelete(String path, Long storeId) {
        return authorizedDelete(path, resolveAccess(storeId));
    }

    public RestClient.RequestHeadersSpec<?> authorizedDelete(String path, FactusAccess access) {
        return restClient.delete().uri(access.baseUrl() + path).header("Authorization", bearer(access));
    }

    public RestClient.RequestBodySpec authorizedPatch(String path) {
        return authorizedPatch(path, (Long) null);
    }

    public RestClient.RequestBodySpec authorizedPatch(String path, Long storeId) {
        return authorizedPatch(path, resolveAccess(storeId));
    }

    public RestClient.RequestBodySpec authorizedPatch(String path, FactusAccess access) {
        return restClient.patch().uri(access.baseUrl() + path).header("Authorization", bearer(access));
    }

    /**
     * Convierte un fallo HTTP de Factus en un {@link FactusApiException} con el mensaje del
     * proveedor. Si ya es de ese tipo lo deja pasar tal cual.
     */
    public RuntimeException translate(String action, Exception ex) {
        if (ex instanceof FactusApiException || ex instanceof com.sciencebot.pos.billing.exception.BillingNotEnabledException) {
            return (RuntimeException) ex;
        }
        if (ex instanceof RestClientResponseException http) {
            int status = http.getStatusCode().value();
            String body = truncate(http.getResponseBodyAsString(), MAX_ERROR_BODY);
            String detail = body == null || body.isBlank() ? "" : ": " + body;
            return new FactusApiException(status, action + " (Factus HTTP " + status + ")" + detail, ex);
        }
        if (ex instanceof ResourceAccessException) {
            return new FactusApiException(0, action + ": no se pudo conectar con Factus (" + ex.getMessage() + ")", ex);
        }
        return new FactusApiException(0, action + ": " + ex.getMessage(), ex);
    }

    public String truncateError(String message) {
        return truncate(message, 1000);
    }

    private String bearer(FactusAccess access) {
        return "Bearer " + tokenManager.getAccessToken(access);
    }

    private static String truncate(String message, int max) {
        if (message == null) return null;
        return message.length() > max ? message.substring(0, max - 3) + "..." : message;
    }
}
