package com.sciencebot.pos.stores;

import java.math.BigDecimal;

public record PlanDto(
    String code,
    String name,
    int maxAdditionalUsers,
    /** Total de usuarios activos permitidos: el administrador más los adicionales. */
    int maxActiveUsers,
    /** Precio mensual en COP; null si aún no está definido. */
    BigDecimal monthlyPrice,
    /** Precio anual en COP; null si aún no está definido. */
    BigDecimal yearlyPrice
) {}
