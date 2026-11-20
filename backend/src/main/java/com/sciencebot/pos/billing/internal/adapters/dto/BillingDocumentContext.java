package com.sciencebot.pos.billing.internal.adapters.dto;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import java.util.Objects;

/**
 * Immutable fiscal routing information. Credentials are resolved at use time and are never persisted here.
 * {@code billingStoreId} is the store whose company and numbering ranges the document uses: its own store, or the
 * main store of the business when the business invoices with a shared tax id.
 */
public record BillingDocumentContext(Long storeId, BillingEnvironment environment, String providerName,
                                     String companyId, Long billingStoreId) {
    public BillingDocumentContext {
        Objects.requireNonNull(storeId, "El documento requiere un local");
        Objects.requireNonNull(environment, "El documento no tiene un entorno fiscal conocido; requiere revisión manual");
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalStateException("El documento no tiene un proveedor fiscal conocido; requiere revisión manual");
        }
        providerName = providerName.trim().toLowerCase();
        if ("alegra".equals(providerName) && (companyId == null || companyId.isBlank())) {
            throw new IllegalStateException("El documento no tiene una empresa fiscal conocida; requiere revisión manual");
        }
        if (billingStoreId == null) {
            billingStoreId = storeId;
        }
    }

    /** A document that bills with its own store's company and ranges. */
    public BillingDocumentContext(Long storeId, BillingEnvironment environment, String providerName, String companyId) {
        this(storeId, environment, providerName, companyId, storeId);
    }
}
