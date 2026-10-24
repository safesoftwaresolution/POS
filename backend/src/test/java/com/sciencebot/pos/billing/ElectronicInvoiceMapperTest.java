package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceDto;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.ElectronicInvoiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas unitarias para ElectronicInvoiceMapper")
class ElectronicInvoiceMapperTest {

    private ElectronicInvoiceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ElectronicInvoiceMapper();
    }

    @Test
    @DisplayName("Debe retornar null si la entidad es nula")
    void toDto_NullEntity_ReturnsNull() {
        assertNull(mapper.toDto(null));
    }

    @Test
    void toDto_ValidEntity_MapsAllFields() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(10L, 1L);
        invoice.setId(50L);
        LocalDateTime now = LocalDateTime.now();
        invoice.markAsValidated("SETP990001", "cufe-abc-123", "https://qr.dian.gov.co", "https://factus.com.co/inv.pdf", now);

        ElectronicInvoiceDto dto = mapper.toDto(invoice);

        assertNotNull(dto);
        assertEquals(50L, dto.id());
        assertEquals(1L, dto.storeId());
        assertEquals(10L, dto.saleId());
        assertEquals("SETP990001", dto.factusNumber());
        assertEquals("cufe-abc-123", dto.cufe());
        assertEquals("https://qr.dian.gov.co", dto.qrCode());
        assertEquals("VALIDATED", dto.status());
        assertNull(dto.errorMessage());
        assertEquals("https://factus.com.co/inv.pdf", dto.pdfUrl());
        assertEquals(now, dto.validatedAt());
    }
}
