package com.sciencebot.pos.inventory;

import java.io.InputStream;

/**
 * Importación masiva de productos e inventario desde una plantilla Excel (.xlsx), acotada al local actual.
 * Un código interno nuevo crea el producto con su stock inicial; uno existente actualiza sus datos y toma la
 * columna Stock como conteo físico (ajuste en el kardex). Es "todo o nada": con una sola fila inválida no se
 * guarda nada.
 */
public interface ProductImportFacade {

    /** Plantilla vacía o, con {@code withProducts}, llena con los productos activos del local y su stock actual. */
    byte[] buildTemplate(boolean withProducts);

    /** Valida el archivo y describe qué pasaría con cada fila, sin escribir nada. */
    ProductImportPreviewDto preview(InputStream xlsx);

    /**
     * Valida e importa el archivo en una sola transacción.
     *
     * @throws ProductImportRejectedException si alguna fila tiene errores (no se guarda nada)
     */
    ProductImportResultDto importProducts(InputStream xlsx);
}
