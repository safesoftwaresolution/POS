package com.sciencebot.pos.finance;

/** Sentido de un movimiento manual de dinero. */
public enum MovementType {
    /** Dinero que sale del local (arriendo, servicios, transporte…). */
    EXPENSE,
    /** Dinero que entra y no viene de una venta del POS (otros ingresos). */
    INCOME
}
