package com.sciencebot.pos.exceptions;

import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.FactusApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FactusExceptionHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private ResponseEntity<Map<String, String>> handle(int upstreamStatus) {
        return handler.handleFactusApi(new FactusApiException(upstreamStatus, "motivo de Factus", null));
    }

    @Test
    void factusRejectingTheDataIsUnprocessableEntityWithItsMessage() {
        ResponseEntity<Map<String, String>> response = handle(422);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("motivo de Factus", response.getBody().get("message"));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, handle(404).getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, handle(400).getStatusCode());
    }

    @Test
    void rejectedCredentialsAndUnavailableFactusAreBadGateway() {
        assertEquals(HttpStatus.BAD_GATEWAY, handle(401).getStatusCode());
        assertEquals(HttpStatus.BAD_GATEWAY, handle(403).getStatusCode());
        assertEquals(HttpStatus.BAD_GATEWAY, handle(500).getStatusCode());
        assertEquals(HttpStatus.BAD_GATEWAY, handle(0).getStatusCode());
    }

    @Test
    void notEnabledStoreIsAConflict() {
        ResponseEntity<Map<String, String>> response =
                handler.handleIllegalState(new BillingNotEnabledException("La facturación no está activa"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("La facturación no está activa", response.getBody().get("message"));
    }
}
