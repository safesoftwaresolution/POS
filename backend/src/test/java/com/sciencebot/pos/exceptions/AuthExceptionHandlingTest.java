package com.sciencebot.pos.exceptions;

import com.sciencebot.pos.auth.EmailNotVerifiedException;
import com.sciencebot.pos.auth.InvalidVerificationTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthExceptionHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidVerificationLinkIsBadRequestWithItsOwnMessage() {
        ResponseEntity<Map<String, String>> response = handler.handleInvalidVerificationToken(
                new InvalidVerificationTokenException("El enlace de verificación venció. Pide un enlace nuevo."));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("InvalidVerificationToken", response.getBody().get("error"));
        assertEquals("El enlace de verificación venció. Pide un enlace nuevo.", response.getBody().get("message"));
    }

    @Test
    void unverifiedLoginIsForbiddenWithTheEmailNotVerifiedCode() {
        ResponseEntity<Map<String, String>> response = handler.handleEmailNotVerified(
                new EmailNotVerifiedException("Debes verificar tu correo electrónico antes de iniciar sesión."));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("EmailNotVerified", response.getBody().get("error"));
        assertEquals("Debes verificar tu correo electrónico antes de iniciar sesión.", response.getBody().get("message"));
    }
}
