package com.sciencebot.pos.inventory.internal.services;

import java.util.List;

/**
 * Columnas de la plantilla de importación masiva, en el orden en que se escriben. Al leer se reconocen por
 * el nombre del encabezado (sin importar mayúsculas, tildes, el asterisco ni el orden), con algunos alias.
 */
enum ImportColumn {

    INTERNAL_CODE("Código interno", true, Kind.TEXT, 18,
            "Identifica el producto en tu local. Si ya existe, la fila actualiza ese producto; si no, lo crea.",
            List.of("codigo", "referencia", "sku")),
    NAME("Nombre", true, Kind.TEXT, 34,
            "Nombre del producto (máximo 100 caracteres).",
            List.of("producto", "nombre del producto")),
    CATEGORY("Categoría", true, Kind.TEXT, 22,
            "Elige una de la lista desplegable.",
            List.of()),
    SUBCATEGORY("Subcategoría", false, Kind.TEXT, 20,
            "Opcional. Si no existe en esa categoría se crea al importar.",
            List.of()),
    BARCODE("Código de barras", false, Kind.TEXT, 18,
            "Opcional. Único en tu local.",
            List.of("codigo barras", "barcode", "ean")),
    PURCHASE_PRICE("Precio de compra", false, Kind.DECIMAL, 16,
            "Costo unitario. Si lo dejas vacío en un producto nuevo queda en 0.",
            List.of("precio compra", "costo")),
    SALE_PRICE("Precio de venta", true, Kind.DECIMAL, 16,
            "Precio al público. Debe ser mayor o igual al precio de compra.",
            List.of("precio venta", "precio")),
    TAX_RATE("IVA %", false, Kind.DECIMAL, 9,
            "0, 5 o 19. Vacío = 0 en productos nuevos.",
            List.of("iva", "iva (%)", "impuesto")),
    STOCK("Stock", false, Kind.INTEGER, 10,
            "Unidades en existencia. En un producto nuevo es el stock inicial; en uno existente es lo que "
                    + "contaste y el sistema ajusta el inventario a ese valor. Vacío = no se toca.",
            List.of("cantidad", "existencias", "stock actual", "stock contado")),
    MIN_STOCK("Stock mínimo", false, Kind.INTEGER, 13,
            "Avisa cuando el stock llega a este valor. Vacío = 5 en productos nuevos.",
            List.of("stock min", "minimo")),
    DESCRIPTION("Descripción", false, Kind.TEXT, 30,
            "Opcional (máximo 255 caracteres).",
            List.of()),
    UNSPSC_CODE("Código UNSPSC", false, Kind.TEXT, 15,
            "Opcional. Código estándar para la factura electrónica DIAN.",
            List.of("unspsc"));

    enum Kind { TEXT, DECIMAL, INTEGER }

    /** Texto del encabezado tal como se escribe en la plantilla (sin el asterisco de obligatorio). */
    final String label;
    /** Obligatoria para crear un producto nuevo (en las actualizaciones, vacío = conservar). */
    final boolean requiredForNew;
    final Kind kind;
    final int width;
    final String help;
    private final List<String> aliases;

    ImportColumn(String label, boolean requiredForNew, Kind kind, int width, String help, List<String> aliases) {
        this.label = label;
        this.requiredForNew = requiredForNew;
        this.kind = kind;
        this.width = width;
        this.help = help;
        this.aliases = aliases;
    }

    String headerText() {
        return requiredForNew ? label + " *" : label;
    }

    /** Columna que corresponde a un encabezado del archivo, o {@code null} si no es una columna conocida. */
    static ImportColumn fromHeader(String header) {
        String key = ProductImportRowParser.normalizeKey(header.replace("*", ""));
        if (key.isEmpty()) {
            return null;
        }
        for (ImportColumn column : values()) {
            if (ProductImportRowParser.normalizeKey(column.label).equals(key)
                    || column.aliases.stream().anyMatch(alias -> ProductImportRowParser.normalizeKey(alias).equals(key))) {
                return column;
            }
        }
        return null;
    }
}
