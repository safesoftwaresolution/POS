package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import com.sciencebot.pos.billing.internal.entities.ElectronicCreditNote;
import com.sciencebot.pos.billing.internal.mappers.ElectronicCreditNoteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas unitarias para ElectronicCreditNoteMapper")
class ElectronicCreditNoteMapperTest {

    private ElectronicCreditNoteMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ElectronicCreditNoteMapper();
    }

    @Test
    @DisplayName("Debe retornar null si la entidad es nula")
    void toDto_NullEntity_ReturnsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    @DisplayName("Debe mapear correctamente todos los campos y traducir el enum de status a String")
    void toDto_ValidEntity_MapsAllFields() {
        ElectronicCreditNote note = ElectronicCreditNote.initialize(
                1L,
                10L,
                "NC-2026-0001",
                "SETP990002519",
                "2",
                "20"
        );
        note.setId(100L);
        LocalDateTime now = LocalDateTime.now();
        note.markAsValidated("NC990000001", "cude-xyz", "https://qr.dian.gov.co", "https://factus.com.co/nc.pdf", now);

        CreditNoteDTO dto = mapper.toDto(note);

        assertNotNull(dto);
        assertEquals(100L, dto.id());
        assertEquals(1L, dto.storeId());
        assertEquals(10L, dto.saleId());
        assertEquals("NC-2026-0001", dto.referenceCode());
        assertEquals("SETP990002519", dto.billNumber());
        assertEquals("NC990000001", dto.creditNoteNumber());
        assertEquals("2", dto.correctionConceptCode());
        assertEquals("20", dto.customizationId());
        assertEquals("cude-xyz", dto.cude());
        assertEquals("https://qr.dian.gov.co", dto.qrCode());
        assertEquals("VALIDATED", dto.status());
        assertNull(dto.errorMessage());
        assertEquals("https://factus.com.co/nc.pdf", dto.pdfUrl());
        assertEquals(now, dto.validatedAt());
    }
}
