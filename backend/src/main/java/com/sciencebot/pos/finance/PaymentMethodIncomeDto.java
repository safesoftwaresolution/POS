package com.sciencebot.pos.finance;

import java.math.BigDecimal;

/** Ingresos del período por método de pago (ventas no a fiado y abonos de fiado). */
public record PaymentMethodIncomeDto(String paymentMethod, BigDecimal total) {}
