package com.sciencebot.pos.billing;

/**
 * Factus rechazó una llamada o no se pudo llegar a Factus. Lleva el mensaje del proveedor para
 * que el usuario sepa qué pasó (credenciales inválidas, resolución inexistente, servicio caído...).
 */
public class FactusApiException extends RuntimeException {

    /** Código HTTP que devolvió Factus, o 0 si ni siquiera hubo respuesta (red caída, timeout). */
    private final int upstreamStatus;

    public FactusApiException(int upstreamStatus, String message, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }

    /** Factus rechazó las credenciales del local. */
    public boolean isAuthenticationError() {
        return upstreamStatus == 401 || upstreamStatus == 403;
    }

    /** No hubo respuesta o Factus falló por su lado: tiene sentido reintentar más tarde. */
    public boolean isTemporary() {
        return upstreamStatus == 0 || upstreamStatus >= 500;
    }
}
