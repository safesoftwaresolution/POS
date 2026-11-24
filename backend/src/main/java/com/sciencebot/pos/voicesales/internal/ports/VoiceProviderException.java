package com.sciencebot.pos.voicesales.internal.ports;

/** Deliberately carries neither upstream content nor a cause with potentially sensitive data. */
public final class VoiceProviderException extends RuntimeException {
    public VoiceProviderException() {
        super("El proveedor de voz no está disponible.");
    }
}
