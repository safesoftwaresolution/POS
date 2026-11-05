package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.exception.BillingProviderException;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BillingProviderExceptionTest {

    private static BillingProviderException withStatus(int status) {
        return new BillingProviderException(status, "motivo", null);
    }

    @Test
    void carriesTheUpstreamStatusMessageAndCause() {
        RuntimeException cause = new RuntimeException("raíz");

        BillingProviderException ex = new BillingProviderException(422, "datos inválidos", cause);

        assertEquals(422, ex.getUpstreamStatus());
        assertEquals("datos inválidos", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void onlyUnauthorizedAndForbiddenAreAuthenticationErrors() {
        assertTrue(withStatus(401).isAuthenticationError());
        assertTrue(withStatus(403).isAuthenticationError());
        assertFalse(withStatus(400).isAuthenticationError());
        assertFalse(withStatus(404).isAuthenticationError());
        assertFalse(withStatus(500).isAuthenticationError());
        assertFalse(withStatus(0).isAuthenticationError());
    }

    @Test
    void noResponseOrServerErrorsAreTemporary() {
        assertTrue(withStatus(0).isTemporary());
        assertTrue(withStatus(500).isTemporary());
        assertTrue(withStatus(503).isTemporary());
        assertFalse(withStatus(401).isTemporary());
        assertFalse(withStatus(422).isTemporary());
        assertFalse(withStatus(499).isTemporary());
    }

    @Test
    void alegraExceptionIsAProviderExceptionWithTheSameBehavior() {
        AlegraApiException alegra = new AlegraApiException(503, "Alegra caído", null);

        assertInstanceOf(BillingProviderException.class, alegra);
        assertEquals(503, alegra.getUpstreamStatus());
        assertEquals("Alegra caído", alegra.getMessage());
        assertTrue(alegra.isTemporary());
        assertFalse(alegra.isAuthenticationError());
        assertTrue(new AlegraApiException(401, "x", null).isAuthenticationError());
    }
}
