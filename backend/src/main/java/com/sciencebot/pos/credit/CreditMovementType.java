package com.sciencebot.pos.credit;

/** Tipo de movimiento en la cuenta de fiado de un cliente. */
public enum CreditMovementType {
    /** Venta a fiado: sube la deuda. */
    CHARGE,
    /** Abono del cliente: baja la deuda. */
    PAYMENT,
    /** Devolución de una venta a fiado: baja la deuda en vez de devolver dinero. */
    RETURN
}
