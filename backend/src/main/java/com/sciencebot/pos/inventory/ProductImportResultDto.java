package com.sciencebot.pos.inventory;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resumen de una importación masiva de productos ya aplicada")
public record ProductImportResultDto(
        @Schema(description = "Filas con datos leídas del archivo", example = "120")
        int totalRows,
        @Schema(description = "Productos creados", example = "100")
        int created,
        @Schema(description = "Productos existentes actualizados (datos y/o stock)", example = "15")
        int updated,
        @Schema(description = "Productos existentes que ya estaban igual", example = "5")
        int unchanged,
        @Schema(description = "Movimientos de inventario registrados (stock inicial y ajustes por conteo)", example = "110")
        int stockMovements,
        @Schema(description = "Subcategorías nuevas creadas para el local", example = "2")
        int subcategoriesCreated,
        @Schema(description = "Productos del archivo que quedaron con stock igual o por debajo de su mínimo", example = "7")
        int lowStockProducts
) {}
