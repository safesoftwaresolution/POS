package com.sciencebot.pos.billing.exception;

/**
 * Alegra rechazó una llamada o no se pudo llegar a ella. Es la versión de Alegra de
 * {@link BillingProviderException}: lleva el código HTTP (0 si no hubo respuesta) y el mensaje del proveedor,
 * nunca el token.
 */
public class AlegraApiException extends BillingProviderException {

    public AlegraApiException(int upstreamStatus, String message, Throwable cause) {
        super(upstreamStatus, message, cause);
    }
}
