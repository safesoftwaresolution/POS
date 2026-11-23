package com.sciencebot.pos.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ingresos y egresos de un día; los días sin movimiento van en cero. */
public record DailyFlowDto(LocalDate date, BigDecimal income, BigDecimal outflow) {}
