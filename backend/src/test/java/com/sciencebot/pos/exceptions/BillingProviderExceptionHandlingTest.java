package com.sciencebot.pos.exceptions;

import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.BillingProviderException;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingProviderExceptionHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // --- La base BillingProviderException (cualquier proveedor) tiene el mismo mapeo ---

    private ResponseEntity<Map<String, String>> handleProvider(int upstreamStatus) {
        return handler.handleBillingProvider(new BillingProviderException(upstreamStatus, "motivo del proveedor", null));
    }

    @Test
    void anyProviderRejectingTheDataIsUnprocessableEntityWithItsMessage() {
        ResponseEntity<Map<String, String>> response = handleProvider(422);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertEquals("motivo del proveedor", response.getBody().get("message"));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, handleProvider(404).getStatusCode());
    }

    @Test
    void anyProviderRejectingCredentialsOrDownIsBadGateway() {
        assertEquals(HttpStatus.BAD_GATEWAY, handleProvider(401).getStatusCode());
        assertEquals(HttpStatus.BAD_GATEWAY, handleProvider(403).getStatusCode());
        assertEquals(HttpStatus.BAD_GATEWAY, handleProvider(503).getStatusCode());
        assertEquals(HttpStatus.BAD_GATEWAY, handleProvider(0).getStatusCode());
    }

    @Test
    void alegraAndBaseHandlersGiveTheSameResponse() {
        for (int status : new int[]{0, 400, 401, 403, 404, 422, 500}) {
            ResponseEntity<Map<String, String>> base = handleProvider(status);
            ResponseEntity<Map<String, String>> alegra =
                    handler.handleBillingProvider(new AlegraApiException(status, "motivo del proveedor", null));
            assertEquals(alegra.getStatusCode(), base.getStatusCode());
            assertEquals(alegra.getBody(), base.getBody());
        }
    }

    // --- Operación no implementada por el proveedor activo ---

    @Test
    void unsupportedOperationIsNotImplementedWithItsMessage() {
        ResponseEntity<Map<String, String>> response = handler.handleUnsupportedOperation(
                new UnsupportedOperationException("Alegra no soporta notas débito"));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertEquals("Not Implemented", response.getBody().get("error"));
        assertEquals("Alegra no soporta notas débito", response.getBody().get("message"));
    }

    @Test
    void unsupportedOperationWithoutMessageGetsAClearDefault() {
        ResponseEntity<Map<String, String>> response =
                handler.handleUnsupportedOperation(new UnsupportedOperationException());

        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
        assertTrue(response.getBody().get("message").contains("proveedor de facturación"));
    }

    @Test
    void notEnabledStoreIsAConflict() {
        ResponseEntity<Map<String, String>> response =
                handler.handleIllegalState(new BillingNotEnabledException("La facturación no está activa"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("La facturación no está activa", response.getBody().get("message"));
    }
}
