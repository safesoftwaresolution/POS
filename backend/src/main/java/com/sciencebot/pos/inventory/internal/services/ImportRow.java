package com.sciencebot.pos.inventory.internal.services;

import java.math.BigDecimal;
import java.util.Map;

/** Una fila con datos de la hoja Productos, tal como se leyó del Excel (sin interpretar). */
record ImportRow(int rowNumber, Map<ImportColumn, ImportCell> cells) {

    ImportCell cell(ImportColumn column) {
        return cells.getOrDefault(column, ImportCell.EMPTY);
    }

    /**
     * Valor crudo de una celda. Las celdas numéricas traen {@code number} (y su texto plano, sin notación
     * científica, para las columnas de código); las de texto solo {@code text}.
     */
    record ImportCell(String text, BigDecimal number, String formulaError) {

        static final ImportCell EMPTY = new ImportCell(null, null, null);

        boolean isBlank() {
            return text == null && number == null && formulaError == null;
        }
    }
}
