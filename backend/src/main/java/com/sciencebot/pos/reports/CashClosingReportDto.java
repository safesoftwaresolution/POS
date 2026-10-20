package com.sciencebot.pos.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "Cierre de caja para un período: cuánto efectivo debería haber en caja y quién lo generó")
public record CashClosingReportDto(
        @Schema(description = "Inicio del período analizado", example = "2025-01-01T00:00:00")
        LocalDateTime dateFrom,
        @Schema(description = "Fin del período analizado", example = "2025-01-01T23:59:59")
        LocalDateTime dateTo,
        @Schema(description = "Monto total vendido por cada método de pago (código del tema PAYMENT_METHODS → monto)")
        Map<String, BigDecimal> totalByPaymentMethod,
        @Schema(description = "Efectivo esperado en caja: suma de ventas en efectivo (CASH) del período")
        BigDecimal expectedCash,
        @Schema(description = "Desglose del efectivo (CASH) vendido por cada vendedor, para rendir cuentas por cajero")
        Map<String, BigDecimal> cashBySeller
) {}
