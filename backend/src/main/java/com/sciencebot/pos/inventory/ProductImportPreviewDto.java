package com.sciencebot.pos.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Vista previa de una importación masiva de productos: qué pasaría con cada fila, sin guardar nada")
public record ProductImportPreviewDto(
        @Schema(description = "Filas con datos leídas del archivo", example = "120")
        int totalRows,
        @Schema(description = "Productos nuevos que se crearían", example = "100")
        int toCreate,
        @Schema(description = "Productos existentes que se actualizarían (datos y/o stock)", example = "15")
        int toUpdate,
        @Schema(description = "Productos existentes sin cambios", example = "3")
        int unchanged,
        @Schema(description = "Filas con errores. Si es mayor que 0 no se importa nada", example = "2")
        int withErrors,
        @Schema(description = "Detalle por fila, en el orden del archivo")
        List<ProductImportRowDto> rows
) {
    public boolean hasErrors() {
        return withErrors > 0;
    }
}
