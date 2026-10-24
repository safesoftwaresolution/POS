package com.sciencebot.pos.inventory;

/**
 * El archivo tiene filas con errores y, como la importación es "todo o nada", no se guardó nada.
 * Lleva la vista previa para que el cliente muestre qué corregir.
 */
public class ProductImportRejectedException extends RuntimeException {

    private final transient ProductImportPreviewDto preview;

    public ProductImportRejectedException(ProductImportPreviewDto preview) {
        super(preview.withErrors() == 1
                ? "1 fila tiene errores; no se importó ningún producto"
                : preview.withErrors() + " filas tienen errores; no se importó ningún producto");
        this.preview = preview;
    }

    public ProductImportPreviewDto getPreview() {
        return preview;
    }
}
