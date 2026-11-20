package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.sciencebot.pos.billing.dto.BillingEnvironment;

/**
 * Con qué token, en qué URL y bajo qué empresa (company.id) se atiende a un local en Alegra. El token es el de
 * plataforma del entorno; lo que aísla a un local de otro es su company.id. Vive solo en memoria durante la llamada.
 * {@code billingStoreId} es el local de cuya empresa y numeración se factura: el propio, o la sucursal principal
 * de un negocio con NIT compartido.
 */
public record AlegraAccess(
        Long storeId,
        BillingEnvironment environment,
        String baseUrl,
        String token,
        String companyId,
        Long billingStoreId
) {

    public AlegraAccess {
        if (billingStoreId == null) {
            billingStoreId = storeId;
        }
    }

    /** Acceso de un local que factura con su propia empresa. */
    public AlegraAccess(Long storeId, BillingEnvironment environment, String baseUrl, String token, String companyId) {
        this(storeId, environment, baseUrl, token, companyId, storeId);
    }

    /** Nunca imprime el token. */
    @Override
    public String toString() {
        return "AlegraAccess[storeId=" + storeId + ", billingStoreId=" + billingStoreId + ", environment=" + environment
                + ", baseUrl=" + baseUrl + ", companyId=" + companyId + "]";
    }
}
