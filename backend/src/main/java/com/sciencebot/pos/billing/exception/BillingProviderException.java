package com.sciencebot.pos.billing.exception;

/**
 * Un proveedor de facturación electrónica (Factus, Alegra, ...) rechazó una llamada o no se pudo
 * llegar a él. Lleva el mensaje del proveedor para que el usuario sepa qué pasó (credenciales
 * inválidas, resolución inexistente, servicio caído...).
 * <p>
 * Es la base común de los errores de cada proveedor: los servicios y el manejador global de
 * excepciones trabajan con esta clase sin conocer a ningún proveedor concreto.
 */
public class BillingProviderException extends RuntimeException {

    /** Código HTTP que devolvió el proveedor, o 0 si ni siquiera hubo respuesta (red caída, timeout). */
    private final int upstreamStatus;

    public BillingProviderException(int upstreamStatus, String message, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    /** El proveedor rechazó las credenciales del local. */
    public boolean isAuthenticationError() {
        return upstreamStatus == 401 || upstreamStatus == 403;
    }

    /** No hubo respuesta o el proveedor falló por su lado: tiene sentido reintentar más tarde. */
    public boolean isTemporary() {
        return upstreamStatus == 0 || upstreamStatus >= 500;
    }
}
