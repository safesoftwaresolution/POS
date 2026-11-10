package com.sciencebot.pos.auth;

import org.springframework.security.authentication.DisabledException;

/** Inicio de sesión de una cuenta que aún no verificó su correo: 403 con el código {@code EmailNotVerified}. */
public class EmailNotVerifiedException extends DisabledException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
