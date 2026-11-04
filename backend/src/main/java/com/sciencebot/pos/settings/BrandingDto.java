package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identidad visible del negocio (nombre y logo), disponible para cualquier usuario del local")
public record BrandingDto(
        @Schema(description = "Nombre comercial", example = "Tienda Don Pepe")
        String businessName,
        @Schema(description = "URL pública del logo; nula si no se ha subido uno")
        String logoUrl
) {}
