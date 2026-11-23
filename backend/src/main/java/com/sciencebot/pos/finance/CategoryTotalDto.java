package com.sciencebot.pos.finance;

import java.math.BigDecimal;

/** Total de gastos de una categoría. */
public record CategoryTotalDto(String category, BigDecimal total) {}
