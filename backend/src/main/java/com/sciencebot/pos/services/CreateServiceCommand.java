package com.sciencebot.pos.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Datos requeridos para crear un servicio")
public record CreateServiceCommand(
    @Schema(description = "Código interno único por tienda", example = "SRV-MASAJE", requiredMode = Schema.RequiredMode.REQUIRED)
    String code,
    @Schema(description = "Nombre del servicio", example = "Masaje relajante 60 min", requiredMode = Schema.RequiredMode.REQUIRED)
    String name,
    @Schema(description = "Descripción del servicio", example = "Masaje de cuerpo completo")
    String description,
    @Schema(description = "ID de la categoría", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    Long categoryId,
    @Schema(description = "Precio de venta con impuesto incluido", example = "120000.00", requiredMode = Schema.RequiredMode.REQUIRED)
    BigDecimal price,
    @Schema(description = "Tasa de IVA (0.00, 5.00, 19.00). Por defecto 0", example = "19.00")
    BigDecimal taxRate,
    @Schema(description = "Código UNSPSC para la factura electrónica DIAN", example = "90111500")
    String unspscCode,
    @Schema(description = "Código de unidad de medida DIAN. Por defecto 94 (unidad)", example = "94")
    String unitMeasureCode,
    @Schema(description = "Duración estimada en minutos (opcional)", example = "60")
    Integer durationMinutes
) {}
