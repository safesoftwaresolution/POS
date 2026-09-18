package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos para actualizar la configuración del negocio")
public record UpdateSettingsCommand(
        @Schema(description = "Nombre comercial del negocio", example = "Tienda ScienceBot")
        String businessName,
        @Schema(description = "Dirección física del negocio", example = "Calle 123 # 45-67, Bogotá")
        String address,
        @Schema(description = "Teléfono del negocio", example = "+57 1 234 5678")
        String phone,
        @Schema(description = "NIT o RUT del negocio", example = "900987654-1")
        String taxId,
        @Schema(description = "Correo electrónico del negocio", example = "info@tienda.com")
        String email,
        @Schema(description = "URL del logo del negocio", example = "https://storage.example.com/logo.png")
        String logoUrl,
        @Schema(description = "Client ID para Factus API", example = "client_xyz")
        String factusClientId,
        @Schema(description = "Client Secret para Factus API (opcional, no modificar si es null o vacío)", example = "secret_123")
        String factusClientSecret,
        @Schema(description = "Username para Factus API", example = "empresa@factus.co")
        String factusUsername,
        @Schema(description = "Contraseña para Factus API (opcional, no modificar si es null o vacío)", example = "password_123")
        String factusPassword,
        @Schema(description = "ID del rango de numeración DIAN en Factus", example = "8")
        Integer factusNumberingRangeId,
        @Schema(description = "Indica si la facturación electrónica Factus está habilitada", example = "true")
        Boolean factusEnabled
) {
    public UpdateSettingsCommand(String businessName, String address, String phone, String taxId, String email, String logoUrl) {
        this(businessName, address, phone, taxId, email, logoUrl, null, null, null, null, null, null);
    }
}
