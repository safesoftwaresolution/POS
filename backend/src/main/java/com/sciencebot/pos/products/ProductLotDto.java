package com.sciencebot.pos.products;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Lote de un producto: unidades que entraron juntas con un mismo vencimiento")
public record ProductLotDto(
        @Schema(description = "ID del lote", example = "7")
        Long id,
        @Schema(description = "Código de lote del proveedor (opcional)", example = "L-2210")
        String lotCode,
        @Schema(description = "Unidades que entraron con este lote", example = "50")
        int quantity,
        @Schema(description = "Unidades que quedan del lote, asumiendo que se vende primero lo que vence primero", example = "32")
        int remainingQuantity,
        @Schema(description = "Fecha de vencimiento del lote", example = "2027-01-15")
        LocalDate expirationDate,
        @Schema(description = "Cuándo entró el lote")
        LocalDateTime receivedAt
) {}
