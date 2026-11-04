package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.sciencebot.pos.billing.dto.BillingEnvironment;

/**
 * Con qué token, en qué URL y bajo qué empresa (company.id) se atiende a un local en Alegra. El token es el de
 * plataforma del entorno; lo que aísla a un local de otro es su company.id. Vive solo en memoria durante la llamada.
 */
public record AlegraAccess(
        Long storeId,
        BillingEnvironment environment,
        String baseUrl,
        String token,
        String companyId
) {

    /** Nunca imprime el token. */
    @Override
    public String toString() {
        return "AlegraAccess[storeId=" + storeId + ", environment=" + environment + ", baseUrl=" + baseUrl
                + ", companyId=" + companyId + "]";
    }
}
