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
        this(OutboundHttp.noRedirectsRestClient());
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

    /** Actualización parcial ({@code PATCH}); mismos encabezados y misma regla de URL que las demás llamadas. */
    public RestClient.RequestBodySpec authorizedPatch(String path, AlegraAccess access) {
        return restClient.patch().uri(access.baseUrl() + path)
                .header("Authorization", bearer(access))
                .header("Accept", "application/json");
    }

    private static final int MAX_REDIRECTS = 3;

    /** Resultado de pedir un enlace: los bytes, o la siguiente URL si Alegra respondió con una redirección. */
    private record Fetched(byte[] data, URI redirect) {}

    /**
     * Descarga un archivo desde un enlace que devolvió Alegra. El enlace de descarga de Alegra redirige a su S3, así
     * que las redirecciones se siguen A MANO: cada salto (incluido el primero) debe ser https de Alegra o de su bucket
     * y ninguno recibe el token. El cliente no sigue redirecciones por su cuenta, y la lectura se corta al superar
     * {@code maxBytes}.
     */
    public byte[] download(String link, int maxBytes) {
        URI uri = trustedDownloadUri(link);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            URI current = uri;
            Fetched fetched = restClient.get().uri(current).exchange((request, response) -> {
                if (response.getStatusCode().is3xxRedirection()) {
                    String location = response.getHeaders().getFirst("Location");
                    if (location == null || location.isBlank()) {
                        throw new RestClientResponseException("Alegra redirigió la descarga sin indicar a dónde",
                                response.getStatusCode(), response.getStatusText(), response.getHeaders(), null, null);
                    }
                    return new Fetched(null, current.resolve(location.trim()));
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new RestClientResponseException("Alegra respondió con un error al descargar el archivo",
                            response.getStatusCode(), response.getStatusText(), response.getHeaders(), null, null);
                }
                byte[] data = response.getBody().readNBytes(maxBytes + 1);
                if (data.length > maxBytes) {
                    throw new IllegalStateException("El archivo que devolvió Alegra supera el tamaño permitido");
                }
                return new Fetched(data, null);
            });
            if (fetched.data() != null) {
                return fetched.data();
            }
            uri = trustedDownloadUri(fetched.redirect().toString());
        }
        throw new IllegalStateException("Alegra redirigió la descarga demasiadas veces");
    }

    private static URI trustedDownloadUri(String link) {
        URI uri;
        try {
            uri = new URI(link == null ? "" : link.trim());
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Alegra devolvió un enlace de descarga inválido");
        }
        String host = uri.getHost();
        String lower = host == null ? "" : host.toLowerCase();
        // Los dominios de Alegra y solo el bucket de S3 con el nombre de Alegra (no cualquier cliente de AWS).
        boolean alegraHost = lower.equals("alegra.com") || lower.endsWith(".alegra.com");
        boolean alegraBucket = lower.startsWith("api-alegra-e-provider-") && lower.endsWith(".amazonaws.com");
        boolean trusted = "https".equalsIgnoreCase(uri.getScheme()) && host != null && uri.getUserInfo() == null
                && (alegraHost || alegraBucket);
        if (!trusted) {
            throw new IllegalStateException("Alegra devolvió un enlace de descarga que no es de confianza");
        }
        return uri;
    }

    /**
     * Convierte un fallo HTTP de Alegra en un {@link AlegraApiException} con el mensaje del proveedor. Un 409 se
     * describe como "conflicto" en lugar de escribir el número (convención heredada de Factus, cuando los servicios
     * de notas leían el texto "409"; ya no hay lógica que dependa de él).
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
            return new AlegraApiException(0, action + ": no se pudo conectar con Alegra ("
                    + withoutQueryStrings(ex.getMessage()) + ")", ex);
        }
        return new AlegraApiException(0, action + ": " + withoutQueryStrings(ex.getMessage()), ex);
    }

    /** Spring incluye la URL completa en los errores de E/S: una URL firmada daría acceso al archivo, así que se oculta la consulta. */
    private static String withoutQueryStrings(String message) {
        return message == null ? null : message.replaceAll("\\?[^\\s\"']*", "?***");
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
