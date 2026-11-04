package com.sciencebot.pos.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Servicio del catálogo (se cobra y se factura, no tiene stock)")
public record ServiceDto(
    @Schema(description = "Identificador único del servicio", example = "1")
    Long id,
    @Schema(description = "Código interno único por tienda", example = "SRV-MASAJE")
    String code,
    @Schema(description = "Nombre del servicio", example = "Masaje relajante 60 min")
    String name,
    @Schema(description = "Descripción del servicio", example = "Masaje de cuerpo completo")
    String description,
    @Schema(description = "ID de la categoría (catálogo global)", example = "3")
    Long categoryId,
    @Schema(description = "Nombre de la categoría", example = "Spa")
    String categoryName,
    @Schema(description = "Precio de venta con impuesto incluido", example = "120000.00")
    BigDecimal price,
    @Schema(description = "Tasa de IVA (0.00, 5.00, 19.00)", example = "19.00")
    BigDecimal taxRate,
    @Schema(description = "Código UNSPSC para la factura electrónica DIAN", example = "90111500")
    String unspscCode,
    @Schema(description = "Código de unidad de medida DIAN (94 = unidad)", example = "94")
    String unitMeasureCode,
    @Schema(description = "Duración estimada en minutos (opcional; la usará la agenda de citas)", example = "60", nullable = true)
    Integer durationMinutes,
    @Schema(description = "Si el servicio está disponible para la venta", example = "true")
    boolean active
) {}
