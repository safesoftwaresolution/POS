package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.api.*;
import com.sciencebot.pos.billing.dto.*;
import com.sciencebot.pos.billing.exception.*;
import com.sciencebot.pos.billing.internal.controllers.BillingInvoiceController;
import com.sciencebot.pos.billing.internal.services.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingInvoiceControllerTest {

    @Mock
    private InvoiceService invoiceService;

    private BillingInvoiceController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new BillingInvoiceController(invoiceService);
    }

    @Test
    void listInvoices_ReturnsPagedInvoices() {
        Pageable pageable = PageRequest.of(0, 10);
        ElectronicInvoiceDto dto = new ElectronicInvoiceDto(
                1L, 10L, 5L, "SETP-001", "cufe-123", "qr-123",
                "VALIDATED", null, "https://factus.com/doc.pdf", LocalDateTime.now()
        );
        when(invoiceService.listInvoices(pageable, "VALIDATED"))
                .thenReturn(new PageImpl<>(List.of(dto), pageable, 1));

        ResponseEntity<Page<ElectronicInvoiceDto>> response = controller.listInvoices(pageable, "VALIDATED");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("SETP-001", response.getBody().getContent().get(0).factusNumber());
    }

    @Test
    void getInvoiceBySaleId_Found_ReturnsInvoice() {
        ElectronicInvoiceDto dto = new ElectronicInvoiceDto(
                1L, 10L, 5L, "SETP-001", "cufe-123", "qr-123",
                "VALIDATED", null, "https://factus.com/doc.pdf", LocalDateTime.now()
        );
        when(invoiceService.getBySaleId(10L)).thenReturn(Optional.of(dto));

        ResponseEntity<ElectronicInvoiceDto> response = controller.getInvoiceBySaleId(10L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("cufe-123", response.getBody().cufe());
    }

    @Test
    void getInvoiceBySaleId_NotFound_Returns404() {
        when(invoiceService.getBySaleId(99L)).thenReturn(Optional.empty());

        ResponseEntity<ElectronicInvoiceDto> response = controller.getInvoiceBySaleId(99L);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void retryInvoice_Success_ReturnsUpdatedInvoice() {
        ElectronicInvoiceDto dto = new ElectronicInvoiceDto(
                1L, 10L, 5L, "SETP-002", "cufe-456", "qr-456",
                "VALIDATED", null, "https://factus.com/doc2.pdf", LocalDateTime.now()
        );
        when(invoiceService.retryInvoice(10L)).thenReturn(dto);

        ResponseEntity<ElectronicInvoiceDto> response = controller.retryInvoice(10L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("SETP-002", response.getBody().factusNumber());
    }

    @Test
    void sendInvoiceEmail_Success_Returns200() {
        SendInvoiceEmailRequest request = new SendInvoiceEmailRequest("cliente@test.com");
        doNothing().when(invoiceService).sendInvoiceEmail(10L, "cliente@test.com");

        ResponseEntity<Void> response = controller.sendInvoiceEmail(10L, request);

        assertEquals(200, response.getStatusCode().value());
        verify(invoiceService).sendInvoiceEmail(10L, "cliente@test.com");
    }

    @Test
    void downloadPdf_ReturnsPdfBytesAndHeaders() {
        byte[] dummyPdf = "%PDF-1.4 test content".getBytes();
        when(invoiceService.downloadInvoicePDF(10L)).thenReturn(dummyPdf);

        ResponseEntity<byte[]> response = controller.downloadPdf(10L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("factura-10.pdf"));
        assertArrayEquals(dummyPdf, response.getBody());
    }

    @Test
    void downloadXml_ReturnsXmlBytesAndHeaders() {
        byte[] dummyXml = "<?xml version=\"1.0\"?><Invoice></Invoice>".getBytes();
        when(invoiceService.downloadInvoiceXML(10L)).thenReturn(dummyXml);

        ResponseEntity<byte[]> response = controller.downloadXml(10L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_XML, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("attachment"));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("factura-10.xml"));
        assertArrayEquals(dummyXml, response.getBody());
    }
}
