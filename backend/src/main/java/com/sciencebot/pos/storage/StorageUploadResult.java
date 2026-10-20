package com.sciencebot.pos.storage;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de carga de archivo en Cloudflare R2")
public record StorageUploadResult(
    @Schema(description = "Clave / Path del objeto en el bucket", example = "products/a1b2c3d4.jpg")
    String key,

    @Schema(description = "URL pública de acceso al archivo", example = "https://pub-example.r2.dev/products/a1b2c3d4.jpg")
    String url,

    @Schema(description = "Nombre original del archivo", example = "arroz_diana.jpg")
    String originalFilename,

    @Schema(description = "Tamaño del archivo en bytes", example = "1048576")
    long sizeBytes,

    @Schema(description = "Tipo MIME del archivo", example = "image/jpeg")
    String contentType
) {}
