package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ElectronicDebitNoteTest {

    private ElectronicDebitNote newNote() {
        return ElectronicDebitNote.initialize(1L, null, " ND-2026-0001 ", "SETP990002519", "1", null);
    }

    @Test
    void initialize_StartsPendingWithDefaultCustomizationAndTrimmedReference() {
        ElectronicDebitNote note = newNote();

        assertEquals(ElectronicInvoiceStatus.PENDING, note.getStatus());
        assertEquals("30", note.getCustomizationId());
        assertEquals("ND-2026-0001", note.getReferenceCode());
    }

    @Test
    void initialize_WithoutBillNumber_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ElectronicDebitNote.initialize(1L, null, "ND-1", " ", "1", "30"));
    }

    @Test
    void markAsValidated_WithoutNumber_ThrowsAndKeepsPreviousState() {
        ElectronicDebitNote note = newNote();
        note.markAsError("Factus no disponible");

        assertThrows(IllegalArgumentException.class,
                () -> note.markAsValidated(" ", "cude", "qr", "pdf", LocalDateTime.now()));

        assertEquals(ElectronicInvoiceStatus.ERROR, note.getStatus());
        assertFalse(note.isValidated());
    }

    @Test
    void markAsValidated_AfterError_ClearsErrorMessage() {
        ElectronicDebitNote note = newNote();
        note.markAsError("Factus no disponible");
        note.prepareForResend();
        note.markAsRejected("Total inválido");

        note.markAsValidated("ND990000001", "cude", "qr", "pdf", null);

        assertTrue(note.isValidated());
        assertNull(note.getErrorMessage());
        assertNotNull(note.getValidatedAt());
    }

    @Test
    void validatedNote_IsFiscallyImmutable() {
        ElectronicDebitNote note = newNote();
        note.markAsValidated("ND990000001", "cude", "qr", "pdf", LocalDateTime.now());

        assertThrows(IllegalStateException.class, () -> note.markAsError("x"));
        assertThrows(IllegalStateException.class, () -> note.markAsRejected("x"));
        assertThrows(IllegalStateException.class, note::prepareForResend);
        assertFalse(note.canBeRetried());
    }
}
