package com.sciencebot.pos.billing.exception;

/**
 * Factus rechazó una llamada o no se pudo llegar a Factus. Lleva el mensaje del proveedor para
 * que el usuario sepa qué pasó (credenciales inválidas, resolución inexistente, servicio caído...).
 * Su comportamiento (código upstream, error de autenticación, error temporal) lo hereda de
 * {@link BillingProviderException}.
 */
public class FactusApiException extends BillingProviderException {

    public FactusApiException(int upstreamStatus, String message, Throwable cause) {
        super(upstreamStatus, message, cause);
    }
}
