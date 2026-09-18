package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Configuración y credenciales de facturación electrónica de una tienda")
public record StoreBillingConfigDto(
        Long storeId,
        boolean factusEnabled,
        String factusClientId,
        String factusClientSecret,
        String factusUsername,
        String factusPassword,
        Integer factusNumberingRangeId
) {}
