package com.sciencebot.pos.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Resultado de validar una fila del archivo de importación masiva")
public record ProductImportRowDto(
        @Schema(description = "Número de fila en el Excel (la fila 1 son los encabezados)", example = "2")
        int rowNumber,
        @Schema(description = "Código interno leído de la fila", example = "PROD-001")
        String internalCode,
        @Schema(description = "Nombre del producto (el del archivo o, si viene vacío, el actual)", example = "Arroz Diana 500g")
        String name,
        @Schema(description = "Qué se hará con la fila: CREATE, UPDATE, UNCHANGED o ERROR", example = "CREATE")
        String action,
        @Schema(description = "Stock actual del producto (null si es nuevo)", example = "12")
        Integer currentStock,
        @Schema(description = "Stock que quedará tras importar (null si no se toca)", example = "15")
        Integer newStock,
        @Schema(description = "Errores que impiden importar el archivo")
        List<String> errors,
        @Schema(description = "Avisos que no impiden importar")
        List<String> warnings
) {}
