package com.sciencebot.pos.billing.internal.config;

import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Falla el arranque si {@code billing.provider} no corresponde a ningún adaptador registrado.
 * Una errata en {@code BILLING_PROVIDER} se descubre al desplegar, no con la primera factura de un cliente.
 */
@Component
class BillingProviderStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(BillingProviderStartupValidator.class);

    private final ActiveBillingProvider activeBillingProvider;

    BillingProviderStartupValidator(ActiveBillingProvider activeBillingProvider) {
        this.activeBillingProvider = activeBillingProvider;
    }

    @PostConstruct
    void validate() {
        if (!activeBillingProvider.isConfiguredProviderAvailable()) {
            throw new IllegalStateException("billing.provider='" + activeBillingProvider.configuredName()
                    + "' no corresponde a ningún proveedor de facturación registrado. Proveedores disponibles: "
                    + activeBillingProvider.availableNames() + ". Corrige BILLING_PROVIDER.");
        }
        log.info("Proveedor de facturación electrónica activo: {}", activeBillingProvider.configuredName());
    }
}
