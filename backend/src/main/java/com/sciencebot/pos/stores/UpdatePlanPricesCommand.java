package com.sciencebot.pos.stores;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Replaces both commercial prices of a plan; a null price clears it ("not defined yet"). */
public record UpdatePlanPricesCommand(
    @PositiveOrZero(message = "El precio mensual no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El precio mensual admite hasta 10 enteros y 2 decimales")
    BigDecimal monthlyPrice,
    @PositiveOrZero(message = "El precio anual no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El precio anual admite hasta 10 enteros y 2 decimales")
    BigDecimal yearlyPrice
) {}
