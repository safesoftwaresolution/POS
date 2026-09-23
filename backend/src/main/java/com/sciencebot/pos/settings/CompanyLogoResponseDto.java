package com.sciencebot.pos.settings;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de la actualización y sincronización del logo de la empresa")
public record CompanyLogoResponseDto(
        @Schema(description = "URL del logo guardado en la base de datos local", example = "https://pub-example.r2.dev/logos/logo.png")
        String logoUrl,

        @Schema(description = "Nombre original del archivo", example = "mi_logo.png")
        String originalFilename,

        @Schema(description = "Ancho en píxeles", example = "250")
        int width,

        @Schema(description = "Alto en píxeles", example = "250")
        int height,

        @Schema(description = "Tamaño del archivo en bytes", example = "102400")
        long sizeBytes,

        @Schema(description = "Indica si fue sincronizado exitosamente con Factus API", example = "true")
        boolean factusSynced,

        @Schema(description = "Mensaje informativo del proceso", example = "Logo actualizado y sincronizado exitosamente con Factus.")
        String message
) {}
