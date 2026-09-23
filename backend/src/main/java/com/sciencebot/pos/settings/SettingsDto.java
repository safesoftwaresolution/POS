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
        String logoUrl
) {}
