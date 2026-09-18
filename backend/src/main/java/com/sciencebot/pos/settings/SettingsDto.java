package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Configuración global del negocio")
public record SettingsDto(
        @Schema(description = "Nombre comercial del negocio", example = "Tienda ScienceBot")
        String businessName,
        @Schema(description = "Dirección física del negocio", example = "Calle 123 # 45-67, Bogotá")
        String address,
        @Schema(description = "Teléfono del negocio", example = "+57 1 234 5678")
        String phone,
        @Schema(description = "NIT o RUT del negocio (usado en facturas)", example = "900987654-1")
        String taxId,
        @Schema(description = "Correo electrónico del negocio", example = "info@tienda.com")
        String email,
        @Schema(description = "URL del logo del negocio", example = "https://storage.example.com/logo.png")
        String logoUrl,
        @Schema(description = "Client ID para Factus API", example = "client_xyz")
        String factusClientId,
        @Schema(description = "Si tiene configurado client_secret en Factus", example = "true")
        Boolean factusHasSecret,
        @Schema(description = "Username para Factus API", example = "empresa@factus.co")
        String factusUsername,
        @Schema(description = "ID del rango de numeración DIAN en Factus", example = "8")
        Integer factusNumberingRangeId,
        @Schema(description = "Indica si la facturación electrónica Factus está habilitada", example = "true")
        boolean factusEnabled
) {
    public SettingsDto(String businessName, String address, String phone, String taxId, String email, String logoUrl) {
        this(businessName, address, phone, taxId, email, logoUrl, null, false, null, null, false);
    }
}
