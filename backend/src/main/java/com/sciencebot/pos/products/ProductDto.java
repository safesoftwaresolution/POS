package com.sciencebot.pos.products;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Datos completos de un producto del catálogo")
public record ProductDto(
    @Schema(description = "Identificador único del producto", example = "1")
    Long id,
    @Schema(description = "Código interno de referencia", example = "PROD-001")
    String internalCode,
    @Schema(description = "Código de barras EAN/UPC", example = "7702011477014")
    String barcode,
    @Schema(description = "Nombre del producto", example = "Arroz Diana 500g")
    String name,
    @Schema(description = "Nombre de la categoría a la que pertenece", example = "Alimentos")
    String categoryName,
    @Schema(description = "ID de la subcategoría propia de la tienda (opcional)", example = "5")
    Long subcategoryId,
    @Schema(description = "Nombre de la subcategoría propia de la tienda (opcional)", example = "Gaseosas")
    String subcategoryName,
    @Schema(description = "Precio de compra (costo)", example = "2500.00")
    BigDecimal purchasePrice,
    @Schema(description = "Precio de venta al público", example = "3200.00")
    BigDecimal salePrice,
    @Schema(description = "Unidades disponibles en inventario", example = "150")
    int quantityAvailable,
    @Schema(description = "Stock mínimo antes de generar alerta de reabastecimiento", example = "20")
    int minStock,
    @Schema(description = "Si el producto está activo y disponible para la venta", example = "true")
    boolean active,
    @Schema(description = "URL de la imagen del producto", example = "https://storage.example.com/products/arroz.jpg")
    String imageUrl,
    @Schema(description = "Tasa de impuesto / IVA del producto (0.00, 5.00, 19.00)", example = "19.00")
    BigDecimal taxRate,
    @Schema(description = "Código estándar UNSPSC para facturación electrónica DIAN", example = "50202301")
    String unspscCode,
    @Schema(description = "ID de la categoría a la que pertenece", example = "2")
    Long categoryId,
    @Schema(description = "Descripción detallada del producto", example = "Arroz blanco de primera calidad")
    String description
) {
    public ProductDto(Long id, String internalCode, String barcode, String name, String categoryName,
                      Long subcategoryId, String subcategoryName, BigDecimal purchasePrice, BigDecimal salePrice,
                      int quantityAvailable, int minStock, boolean active, String imageUrl) {
        this(id, internalCode, barcode, name, categoryName, subcategoryId, subcategoryName, purchasePrice, salePrice,
                quantityAvailable, minStock, active, imageUrl, BigDecimal.ZERO, null, null, null);
    }
}
