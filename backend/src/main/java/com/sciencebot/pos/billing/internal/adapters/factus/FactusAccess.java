package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.BillingEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Credenciales ya descifradas de un local para el entorno con el que factura, más la URL
 * de Factus de ese entorno. Vive solo en memoria durante la llamada.
 */
public record FactusAccess(
        Long storeId,
        BillingEnvironment environment,
        String baseUrl,
        String clientId,
        String clientSecret,
        String username,
        String password,
        Integer numberingRangeId
) {

    /**
     * Cambia si cambia cualquier credencial. Permite descartar un token guardado cuando el
     * super admin actualiza las credenciales del local.
     */
    String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : new String[]{baseUrl, clientId, clientSecret, username, password}) {
                digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    /** Nunca imprime secretos. */
    @Override
    public String toString() {
        return "FactusAccess[storeId=" + storeId + ", environment=" + environment + ", baseUrl=" + baseUrl + "]";
    }
}
