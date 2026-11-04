package com.sciencebot.pos.services;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Datos para actualizar un servicio existente (el código no cambia)")
public record UpdateServiceCommand(
    @Schema(description = "Nuevo nombre", example = "Masaje relajante 90 min")
    String name,
    @Schema(description = "Nueva descripción", example = "Masaje de cuerpo completo")
    String description,
    @Schema(description = "Nueva categoría", example = "3")
    Long categoryId,
    @Schema(description = "Nuevo precio de venta con impuesto incluido", example = "150000.00")
    BigDecimal price,
    @Schema(description = "Nueva tasa de IVA", example = "19.00")
    BigDecimal taxRate,
    @Schema(description = "Nuevo código UNSPSC", example = "90111500")
    String unspscCode,
    @Schema(description = "Nuevo código de unidad de medida DIAN", example = "94")
    String unitMeasureCode,
    @Schema(description = "Nueva duración estimada en minutos (opcional)", example = "90")
    Integer durationMinutes
) {}
