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
}
