package com.sciencebot.pos.billing;

/**
 * El local no puede usar la facturación electrónica todavía: no la pidió, no está activa,
 * o le faltan credenciales o rango de numeración. Se responde como 409 (conflicto de estado).
 */
public class BillingNotEnabledException extends IllegalStateException {

    public BillingNotEnabledException(String message) {
        super(message);
    }
}
