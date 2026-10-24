package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import com.sciencebot.pos.billing.internal.controllers.BillingCreditNoteController;
import com.sciencebot.pos.billing.internal.services.CreditNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingCreditNoteControllerTest {

    @Mock
    private CreditNoteService creditNoteService;

    private BillingCreditNoteController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new BillingCreditNoteController(creditNoteService);
    }

    private CreditNoteDTO sampleDTO() {
        return new CreditNoteDTO(
                1L, 1L, 10L, "NOTA-2026-0015", "SETP990002519", "NC990000001",
                "2", "20", "cude-12345", "qr-url", "VALIDATED", null, "https://factus.com/nc.pdf",
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void createAndValidateCreditNote_Returns200WithDto() {
        CreateCreditNoteRequest request = new CreateCreditNoteRequest(
                "NOTA-2026-0015", "2", "20", "SETP990002519", 1776, "obs", List.of(), null, List.of()
        );
        CreditNoteDTO expected = sampleDTO();

        when(creditNoteService.createAndValidateCreditNote(request)).thenReturn(expected);

        ResponseEntity<CreditNoteDTO> response = controller.createAndValidateCreditNote(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("NC990000001", response.getBody().creditNoteNumber());
        verify(creditNoteService).createAndValidateCreditNote(request);
    }

    @Test
    void listCreditNotes_ReturnsPagedDtos() {
        Pageable pageable = PageRequest.of(0, 10);
        when(creditNoteService.listCreditNotes(pageable, "VALIDATED"))
                .thenReturn(new PageImpl<>(List.of(sampleDTO()), pageable, 1));

        ResponseEntity<Page<CreditNoteDTO>> response = controller.listCreditNotes(pageable, "VALIDATED");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("NOTA-2026-0015", response.getBody().getContent().get(0).referenceCode());
    }

    @Test
    void getCreditNoteById_Found_Returns200() {
        when(creditNoteService.getById(1L)).thenReturn(Optional.of(sampleDTO()));

        ResponseEntity<CreditNoteDTO> response = controller.getCreditNoteById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("NC990000001", response.getBody().creditNoteNumber());
    }

    @Test
    void getCreditNoteById_NotFound_Returns404() {
        when(creditNoteService.getById(99L)).thenReturn(Optional.empty());

        ResponseEntity<CreditNoteDTO> response = controller.getCreditNoteById(99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getCreditNoteByReferenceCode_Found_Returns200() {
        when(creditNoteService.getByReferenceCode("NOTA-2026-0015")).thenReturn(Optional.of(sampleDTO()));

        ResponseEntity<CreditNoteDTO> response = controller.getCreditNoteByReferenceCode("NOTA-2026-0015");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("cude-12345", response.getBody().cude());
    }

    @Test
    void downloadPdf_ReturnsPdfBytes() {
        byte[] dummyPdf = "%PDF-1.4 test nc".getBytes();
        when(creditNoteService.downloadCreditNotePDF(1L)).thenReturn(dummyPdf);

        ResponseEntity<byte[]> response = controller.downloadPdf(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("nota-credito-1.pdf"));
        assertArrayEquals(dummyPdf, response.getBody());
    }

    @Test
    void downloadPdf_NotFound_Returns404() {
        when(creditNoteService.downloadCreditNotePDF(99L)).thenReturn(null);

        ResponseEntity<byte[]> response = controller.downloadPdf(99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void downloadXml_ReturnsXmlBytes() {
        byte[] dummyXml = "<CreditNote>xml</CreditNote>".getBytes();
        when(creditNoteService.downloadCreditNoteXML(1L)).thenReturn(dummyXml);

        ResponseEntity<byte[]> response = controller.downloadXml(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_XML, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("attachment"));
        assertArrayEquals(dummyXml, response.getBody());
    }

    @Test
    void deleteUnvalidatedCreditNote_Success_Returns204() {
        when(creditNoteService.deleteUnvalidatedCreditNote("NOTA-01")).thenReturn(true);

        ResponseEntity<Void> response = controller.deleteUnvalidatedCreditNote("NOTA-01");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(creditNoteService).deleteUnvalidatedCreditNote("NOTA-01");
    }

    @Test
    void deleteUnvalidatedCreditNote_Failure_Returns400() {
        when(creditNoteService.deleteUnvalidatedCreditNote("NOTA-01")).thenReturn(false);

        ResponseEntity<Void> response = controller.deleteUnvalidatedCreditNote("NOTA-01");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createFromSale_Returns200WithDto() {
        when(creditNoteService.createFromSale(10L, "2", "anulación")).thenReturn(sampleDTO());

        ResponseEntity<CreditNoteDTO> response = controller.createFromSale(10L, "2", "anulación");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("NC990000001", response.getBody().creditNoteNumber());
        verify(creditNoteService).createFromSale(10L, "2", "anulación");
    }
}
