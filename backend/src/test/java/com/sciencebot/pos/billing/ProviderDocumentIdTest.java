package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.entities.ElectronicCreditNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** El id del documento en el proveedor se asigna una sola vez y no cambia despues. */
class ProviderDocumentIdTest {

    @Test
    void invoice_StartsWithoutIdAssignsOnceAndIsIdempotentForTheSameValue() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(1L, 2L);
        assertNull(invoice.getProviderDocumentId());

        invoice.assignProviderDocumentId(" 01M6KHB2BJYMHWK4SH6KRESBH1 ");
        invoice.assignProviderDocumentId("01M6KHB2BJYMHWK4SH6KRESBH1");

        assertEquals("01M6KHB2BJYMHWK4SH6KRESBH1", invoice.getProviderDocumentId());
        assertThrows(IllegalStateException.class, () -> invoice.assignProviderDocumentId("OTRO"));
        assertEquals("01M6KHB2BJYMHWK4SH6KRESBH1", invoice.getProviderDocumentId());
    }

    @Test
    void invoice_RejectsBlankId() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(1L, 2L);
        assertThrows(IllegalArgumentException.class, () -> invoice.assignProviderDocumentId(" "));
        assertThrows(IllegalArgumentException.class, () -> invoice.assignProviderDocumentId(null));
        assertNull(invoice.getProviderDocumentId());
    }

    @Test
    void creditNote_AssignsOnce() {
        ElectronicCreditNote note = ElectronicCreditNote.initialize(1L, null, "NC-1", "SETP1", "2", null);
        assertNull(note.getProviderDocumentId());

        note.assignProviderDocumentId("abc");

        assertEquals("abc", note.getProviderDocumentId());
        assertThrows(IllegalStateException.class, () -> note.assignProviderDocumentId("def"));
    }

    @Test
    void debitNote_AssignsOnce() {
        ElectronicDebitNote note = ElectronicDebitNote.initialize(1L, null, "ND-1", "SETP1", "1", null);
        assertNull(note.getProviderDocumentId());

        note.assignProviderDocumentId("abc");

        assertEquals("abc", note.getProviderDocumentId());
        assertThrows(IllegalStateException.class, () -> note.assignProviderDocumentId("def"));
    }

    @Test
    void trackProviderDocument_ReplacesTheIdOfADocumentThatWasNotValidatedButNeverOneThatWas() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(1L, 2L);
        invoice.trackProviderDocument("doc-1");
        invoice.trackProviderDocument("doc-2"); // un documento rechazado se reemplaza por el de la nueva emision
        assertEquals("doc-2", invoice.getProviderDocumentId());

        invoice.markAsValidated("SETP-1", "cufe", "qr", null, java.time.LocalDateTime.now());
        invoice.trackProviderDocument("doc-2");
        assertThrows(IllegalStateException.class, () -> invoice.trackProviderDocument("doc-3"));
        assertEquals("doc-2", invoice.getProviderDocumentId());
    }

    @Test
    void trackProviderDocument_IgnoresABlankId() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(1L, 2L);
        invoice.trackProviderDocument(null);
        invoice.trackProviderDocument(" ");
        assertNull(invoice.getProviderDocumentId());
    }

    @Test
    void awaitsProviderResponse_OnlyWhilePendingWithAnId() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(1L, 2L);
        assertFalse(invoice.awaitsProviderResponse());
        invoice.trackProviderDocument("doc-1");
        assertTrue(invoice.awaitsProviderResponse());
        invoice.markAsRejected("NIT inválido");
        assertFalse(invoice.awaitsProviderResponse());
    }

    @Test
    void aManualRetryKeepsTheIdOfADocumentStillWaitingAndDropsTheOneOfARejectedOrFailedDocument() {
        ElectronicInvoice waiting = ElectronicInvoice.initialize(1L, 2L);
        waiting.trackProviderDocument("doc-1");
        waiting.prepareForRetry();
        assertEquals("doc-1", waiting.getProviderDocumentId());

        ElectronicInvoice rejected = ElectronicInvoice.initialize(1L, 3L);
        rejected.trackProviderDocument("doc-2");
        rejected.markAsRejected("NIT inválido");
        rejected.prepareForRetry();
        assertNull(rejected.getProviderDocumentId(), "un retry tras un rechazo emite un documento nuevo");
    }
}
