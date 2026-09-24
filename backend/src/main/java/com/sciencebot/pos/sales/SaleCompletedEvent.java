package com.sciencebot.pos.sales;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Evento de dominio publicado tras el registro y persistencia exitosa de una venta en el POS.
 */
public record SaleCompletedEvent(
        Long saleId,
        Long storeId,
        String invoiceNumber,
        BigDecimal totalAmount,
        boolean sendToFactus,
        LocalDateTime completedAt
) {}
