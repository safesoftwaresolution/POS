package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;
import com.sciencebot.pos.billing.internal.mappers.ElectronicDebitNoteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas unitarias para ElectronicDebitNoteMapper")
class ElectronicDebitNoteMapperTest {

    private ElectronicDebitNoteMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ElectronicDebitNoteMapper();
    }

    @Test
    @DisplayName("Debe retornar null si la entidad es nula")
    void toDto_NullEntity_ReturnsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    @DisplayName("Debe mapear correctamente todos los campos de ElectronicDebitNote a DebitNoteDTO")
    void toDto_ValidEntity_MapsAllFields() {
        ElectronicDebitNote note = ElectronicDebitNote.initialize(
                1L,
                10L,
                "ND-2026-0001",
                "SETP990002519",
                "3",
                "30"
        );
        note.setId(99L);
        LocalDateTime now = LocalDateTime.now();
        note.markAsValidated("ND990000001", "cude-abc-123", "https://qr.dian.gov.co", "https://factus.com.co/nd.pdf", now);

        DebitNoteDTO dto = mapper.toDto(note);

        assertNotNull(dto);
        assertEquals(99L, dto.id());
        assertEquals(1L, dto.storeId());
        assertEquals(10L, dto.saleId());
        assertEquals("ND-2026-0001", dto.referenceCode());
        assertEquals("SETP990002519", dto.billNumber());
        assertEquals("ND990000001", dto.debitNoteNumber());
        assertEquals("3", dto.correctionConceptCode());
        assertEquals("30", dto.customizationId());
        assertEquals("cude-abc-123", dto.cude());
        assertEquals("https://qr.dian.gov.co", dto.qrCode());
        assertEquals("VALIDATED", dto.status());
        assertNull(dto.errorMessage());
        assertEquals("https://factus.com.co/nd.pdf", dto.pdfUrl());
        assertEquals(now, dto.validatedAt());
    }
}
