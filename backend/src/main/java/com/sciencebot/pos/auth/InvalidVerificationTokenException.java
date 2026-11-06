package com.sciencebot.pos.auth;

/**
 * El enlace de verificación de correo no sirve (falta, no existe, ya se usó o venció). Se responde 400 con un
 * mensaje claro para la persona, en vez del genérico "usuario o contraseña incorrectos" de las credenciales.
 */
public class InvalidVerificationTokenException extends RuntimeException {
    public InvalidVerificationTokenException(String message) {
        super(message);
    }
}
