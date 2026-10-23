package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de probar las credenciales de un local contra el proveedor de facturación, sin activar nada")
public record BillingConnectionTestResult(
        boolean success,
        BillingEnvironment environment,
        String message
) {
    public static BillingConnectionTestResult success(BillingEnvironment environment, String providerName) {
        return new BillingConnectionTestResult(true, environment,
                "Conexión exitosa con " + providerName + " (" + environment + ")");
    }

    public static BillingConnectionTestResult failure(BillingEnvironment environment, String message) {
        return new BillingConnectionTestResult(false, environment, message);
    }
}
