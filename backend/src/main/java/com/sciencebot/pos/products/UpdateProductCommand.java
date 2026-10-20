package com.sciencebot.pos.products;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Datos para actualizar un producto existente")
public record UpdateProductCommand(
    @Schema(description = "Nuevo nombre del producto", example = "Arroz Diana 500g Premium")
    String name,
    @Schema(description = "Nueva descripción", example = "Arroz blanco de primera calidad")
    String description,
    @Schema(description = "Nuevo ID de categoría", example = "2")
    Long categoryId,
    @Schema(description = "Nuevo ID de subcategoría propia de la tienda (opcional, debe pertenecer a categoryId)", example = "5")
    Long subcategoryId,
    @Schema(description = "Nuevo precio de compra", example = "2800.00")
    BigDecimal purchasePrice,
    @Schema(description = "Nuevo precio de venta", example = "3500.00")
    BigDecimal salePrice,
    @Schema(description = "Código de barras", example = "7702011477014")
    String barcode,
    @Schema(description = "Stock mínimo para alerta", example = "20")
    int minStock,
    @Schema(description = "URL de imagen del producto", example = "https://storage.example.com/products/arroz.jpg")
    String imageUrl,
    @Schema(description = "Nueva tasa de impuesto / IVA del producto (0.00, 5.00, 19.00)", example = "19.00")
    BigDecimal taxRate,
    @Schema(description = "Nuevo código estándar UNSPSC para facturación electrónica DIAN", example = "50202301")
    String unspscCode
) {
    public UpdateProductCommand(String name, String description, Long categoryId, Long subcategoryId,
                                BigDecimal purchasePrice, BigDecimal salePrice, String barcode, int minStock, String imageUrl) {
        this(name, description, categoryId, subcategoryId, purchasePrice, salePrice, barcode, minStock, imageUrl, null, null);
    }
}
