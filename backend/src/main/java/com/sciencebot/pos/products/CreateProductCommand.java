package com.sciencebot.pos.products;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Datos requeridos para crear un nuevo producto")
public record CreateProductCommand(
    @Schema(description = "Código interno de referencia único", example = "PROD-001", requiredMode = Schema.RequiredMode.REQUIRED)
    String internalCode,
    @Schema(description = "Código de barras EAN/UPC (opcional)", example = "7702011477014")
    String barcode,
    @Schema(description = "Nombre del producto", example = "Arroz Diana 500g", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,
    @Schema(description = "ID de la categoría a la que pertenece", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    Long categoryId,
    @Schema(description = "ID de la subcategoría propia de la tienda (opcional, debe pertenecer a categoryId)", example = "5")
    Long subcategoryId,
    @Schema(description = "Precio de compra (costo unitario)", example = "2500.00", requiredMode = Schema.RequiredMode.REQUIRED)
    BigDecimal purchasePrice,
    @Schema(description = "Precio de venta al público", example = "3200.00", requiredMode = Schema.RequiredMode.REQUIRED)
    BigDecimal salePrice,
    @Schema(description = "Stock mínimo para alerta de reabastecimiento", example = "20")
    int minStock,
    @Schema(description = "Cantidad de stock inicial disponible", example = "50")
    int quantityAvailable,
    @Schema(description = "Descripción detallada del producto", example = "Arroz blanco de primera calidad")
    String description,
    @Schema(description = "URL de imagen del producto", example = "https://storage.example.com/products/arroz.jpg")
    String imageUrl,
    @Schema(description = "Tasa de impuesto / IVA del producto (0.00, 5.00, 19.00)", example = "19.00")
    BigDecimal taxRate,
    @Schema(description = "Código estándar UNSPSC para facturación electrónica DIAN", example = "50202301")
    String unspscCode,
    @Schema(description = "Vencimiento del stock inicial (opcional). Si se envía exige quantityAvailable > 0 y crea un lote con esas unidades",
            example = "2026-12-31", nullable = true)
    LocalDate expirationDate
) {
    public CreateProductCommand(String internalCode, String barcode, String name, Long categoryId, Long subcategoryId,
                                BigDecimal purchasePrice, BigDecimal salePrice, int minStock, String description, String imageUrl) {
        this(internalCode, barcode, name, categoryId, subcategoryId, purchasePrice, salePrice, minStock, 0, description, imageUrl, BigDecimal.ZERO, null, null);
    }

    public CreateProductCommand(String internalCode, String barcode, String name, Long categoryId, Long subcategoryId,
                                BigDecimal purchasePrice, BigDecimal salePrice, int minStock, int quantityAvailable,
                                String description, String imageUrl, BigDecimal taxRate, String unspscCode) {
        this(internalCode, barcode, name, categoryId, subcategoryId, purchasePrice, salePrice, minStock, quantityAvailable,
                description, imageUrl, taxRate, unspscCode, null);
    }
}
