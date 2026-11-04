package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.sciencebot.pos.billing.exception.AlegraApiException;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.config.http.OutboundHttp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comunicación HTTP autorizada con Alegra (Bearer). Cada llamada recibe el {@link AlegraAccess} del local, así
 * que no existe una empresa global a la que recurrir. Usa {@link OutboundHttp#restClient()} para tener tiempos
 * límite de conexión y lectura. Los errores se traducen a {@link AlegraApiException} sin incluir nunca el token.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraClientHelper {

    private static final int MAX_ERROR_BODY = 500;
    /** Errores de Alegra: {"errors":[{"code":"AEP1002","message":"Company not found"}]}. */
    private static final Pattern ERROR_ITEM =
            Pattern.compile("\"code\"\\s*:\\s*\"([^\"]*)\"[^}]*?\"message\"\\s*:\\s*\"([^\"]*)\"");

    private final RestClient restClient;

    @Autowired
    public AlegraClientHelper() {
        this(OutboundHttp.restClient());
    }

    public AlegraClientHelper(RestClient restClient) {
        this.restClient = restClient != null ? restClient : OutboundHttp.restClient();
    }

    public RestClient.RequestHeadersSpec<?> authorizedGet(String path, AlegraAccess access) {
        return restClient.get().uri(access.baseUrl() + path)
                .header("Authorization", bearer(access))
                .header("Accept", "application/json");
    }

    public RestClient.RequestBodySpec authorizedPost(String path, AlegraAccess access) {
        return restClient.post().uri(access.baseUrl() + path)
                .header("Authorization", bearer(access))
                .header("Accept", "application/json");
    }

    /**
     * Descarga un archivo desde un enlace que devolvió Alegra. Solo se siguen enlaces https de Alegra o de su S3
     * (el enlace trae su propia firma y NO recibe el token) y la lectura se corta al superar {@code maxBytes}.
     */
    public byte[] download(String link, int maxBytes) {
        URI uri = trustedDownloadUri(link);
        return restClient.get().uri(uri).exchange((request, response) -> {
            if (response.getStatusCode().isError()) {
                throw new RestClientResponseException("Alegra respondió con un error al descargar el archivo",
                        response.getStatusCode(), response.getStatusText(), response.getHeaders(), null, null);
            }
            byte[] data = response.getBody().readNBytes(maxBytes + 1);
            if (data.length > maxBytes) {
                throw new IllegalStateException("El archivo que devolvió Alegra supera el tamaño permitido");
            }
            return data;
        });
    }

    private static URI trustedDownloadUri(String link) {
        URI uri;
        try {
            uri = new URI(link == null ? "" : link.trim());
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Alegra devolvió un enlace de descarga inválido");
        }
        String host = uri.getHost();
        boolean trusted = "https".equalsIgnoreCase(uri.getScheme()) && host != null && uri.getUserInfo() == null
                && (host.equalsIgnoreCase("alegra.com") || host.toLowerCase().endsWith(".alegra.com")
                || host.toLowerCase().endsWith(".amazonaws.com"));
        if (!trusted) {
            throw new IllegalStateException("Alegra devolvió un enlace de descarga que no es de confianza");
        }
        return uri;
    }

    /**
     * Convierte un fallo HTTP de Alegra en un {@link AlegraApiException} con el mensaje del proveedor. Un 409 se
     * describe como "conflicto" en lugar de escribir el número: los servicios de notas leen el texto "409" como el
     * conflicto de documentos pendientes de Factus y borrarían el documento.
     */
    public RuntimeException translate(String action, Exception ex) {
        if (ex instanceof AlegraApiException || ex instanceof BillingNotEnabledException) {
            return (RuntimeException) ex;
        }
        if (ex instanceof RestClientResponseException http) {
            int status = http.getStatusCode().value();
            String detail = describeBody(http.getResponseBodyAsString());
            String label = status == 409 ? "conflicto" : String.valueOf(status);
            String hint = (status == 401 || status == 403) ? ": Alegra rechazó el token del servidor" : "";
            return new AlegraApiException(status,
                    action + " (Alegra HTTP " + label + ")" + hint + (detail.isEmpty() ? "" : ": " + detail), ex);
        }
        if (ex instanceof ResourceAccessException) {
            return new AlegraApiException(0, action + ": no se pudo conectar con Alegra (" + ex.getMessage() + ")", ex);
        }
        return new AlegraApiException(0, action + ": " + ex.getMessage(), ex);
    }

    private static String describeBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        Matcher matcher = ERROR_ITEM.matcher(body);
        while (matcher.find()) {
            items.add(matcher.group(1) + " " + matcher.group(2));
        }
        return truncate(items.isEmpty() ? body.trim() : String.join("; ", items), MAX_ERROR_BODY);
    }

    private static String bearer(AlegraAccess access) {
        return "Bearer " + access.token();
    }

    private static String truncate(String message, int max) {
        return message.length() > max ? message.substring(0, max - 3) + "..." : message;
    }
}
