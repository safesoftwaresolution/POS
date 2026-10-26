package com.sciencebot.pos.reports;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Lote vencido o próximo a vencer de un producto")
public record ExpiringProductDto(
        @Schema(description = "ID del producto", example = "1")
        Long productId,
        @Schema(description = "Nombre del producto", example = "Leche entera 1L")
        String productName,
        @Schema(description = "Código interno del producto", example = "PROD-001")
        String internalCode,
        @Schema(description = "ID del lote", example = "7")
        Long lotId,
        @Schema(description = "Código de lote del proveedor (puede ser null)", example = "L-2210", nullable = true)
        String lotCode,
        @Schema(description = "Fecha de vencimiento del lote", example = "2026-11-15")
        LocalDate expirationDate,
        @Schema(description = "Días que faltan para vencer; negativo si ya venció", example = "12")
        long daysToExpire,
        @Schema(description = "Unidades que quedan de este lote, asumiendo que se vende primero lo que vence primero", example = "24")
        int lotRemaining,
        @Schema(description = "Unidades totales del producto en inventario", example = "60")
        int quantityAvailable,
        @Schema(description = "EXPIRED (ya venció), TODAY (vence hoy) o EXPIRING (vence dentro de la ventana)",
                example = "EXPIRING", allowableValues = {"EXPIRED", "TODAY", "EXPIRING"})
        String status
) {}
