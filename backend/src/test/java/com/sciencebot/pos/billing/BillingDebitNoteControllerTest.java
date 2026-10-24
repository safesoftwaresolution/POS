package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import com.sciencebot.pos.billing.internal.controllers.BillingDebitNoteController;
import com.sciencebot.pos.billing.internal.services.DebitNoteService;
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

class BillingDebitNoteControllerTest {

    @Mock
    private DebitNoteService debitNoteService;

    private BillingDebitNoteController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new BillingDebitNoteController(debitNoteService);
    }

    private DebitNoteDTO sampleDTO() {
        return new DebitNoteDTO(
                1L, 1L, 10L, "ND-2026-0001", "SETP990002519", "ND990000001",
                "1", "30", "cude-12345", "qr-url", "VALIDATED", null, "https://factus.com/nd.pdf",
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private CreateDebitNoteRequest sampleRequest() {
        return new CreateDebitNoteRequest(
                "ND-2026-0001", "1", "30", "SETP990002519", 1776, "Intereses", List.of(), null, List.of()
        );
    }

    @Test
    void createAndValidateDebitNote_Returns200WithDto() {
        CreateDebitNoteRequest request = sampleRequest();
        when(debitNoteService.createAndValidateDebitNote(request)).thenReturn(sampleDTO());

        ResponseEntity<DebitNoteDTO> response = controller.createAndValidateDebitNote(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ND990000001", response.getBody().debitNoteNumber());
        verify(debitNoteService).createAndValidateDebitNote(request);
    }

    @Test
    void listDebitNotes_ReturnsPagedDtos() {
        Pageable pageable = PageRequest.of(0, 10);
        when(debitNoteService.listDebitNotes(pageable, "VALIDATED"))
                .thenReturn(new PageImpl<>(List.of(sampleDTO()), pageable, 1));

        ResponseEntity<Page<DebitNoteDTO>> response = controller.listDebitNotes(pageable, "VALIDATED");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("ND-2026-0001", response.getBody().getContent().get(0).referenceCode());
    }

    @Test
    void getDebitNoteById_Found_Returns200() {
        when(debitNoteService.getById(1L)).thenReturn(Optional.of(sampleDTO()));

        ResponseEntity<DebitNoteDTO> response = controller.getDebitNoteById(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ND990000001", response.getBody().debitNoteNumber());
    }

    @Test
    void getDebitNoteById_NotFound_Returns404() {
        when(debitNoteService.getById(99L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.getDebitNoteById(99L).getStatusCode());
    }

    @Test
    void getDebitNoteByReferenceCode_Found_Returns200() {
        when(debitNoteService.getByReferenceCode("ND-2026-0001")).thenReturn(Optional.of(sampleDTO()));

        ResponseEntity<DebitNoteDTO> response = controller.getDebitNoteByReferenceCode("ND-2026-0001");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("cude-12345", response.getBody().cude());
    }

    @Test
    void getDebitNoteByReferenceCode_NotFound_Returns404() {
        when(debitNoteService.getByReferenceCode("ND-X")).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, controller.getDebitNoteByReferenceCode("ND-X").getStatusCode());
    }

    @Test
    void downloadPdf_ReturnsPdfBytes() {
        byte[] dummyPdf = "%PDF-1.4 test nd".getBytes();
        when(debitNoteService.downloadDebitNotePDF(1L)).thenReturn(dummyPdf);

        ResponseEntity<byte[]> response = controller.downloadPdf(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("nota-debito-1.pdf"));
        assertArrayEquals(dummyPdf, response.getBody());
    }

    @Test
    void downloadPdf_EmptyFromProvider_Returns404() {
        when(debitNoteService.downloadDebitNotePDF(1L)).thenReturn(null);

        assertEquals(HttpStatus.NOT_FOUND, controller.downloadPdf(1L).getStatusCode());
    }

    @Test
    void downloadXml_ReturnsXmlBytes() {
        byte[] dummyXml = "<DebitNote>xml</DebitNote>".getBytes();
        when(debitNoteService.downloadDebitNoteXML(1L)).thenReturn(dummyXml);

        ResponseEntity<byte[]> response = controller.downloadXml(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_XML, response.getHeaders().getContentType());
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("nota-debito-1.xml"));
        assertArrayEquals(dummyXml, response.getBody());
    }

    @Test
    void deleteUnvalidatedDebitNote_Success_Returns204() {
        when(debitNoteService.deleteUnvalidatedDebitNote("ND-01")).thenReturn(true);

        assertEquals(HttpStatus.NO_CONTENT, controller.deleteUnvalidatedDebitNote("ND-01").getStatusCode());
        verify(debitNoteService).deleteUnvalidatedDebitNote("ND-01");
    }

    @Test
    void deleteUnvalidatedDebitNote_Failure_Returns400() {
        when(debitNoteService.deleteUnvalidatedDebitNote("ND-01")).thenReturn(false);

        assertEquals(HttpStatus.BAD_REQUEST, controller.deleteUnvalidatedDebitNote("ND-01").getStatusCode());
    }

    @Test
    void createForSale_Returns200WithDto() {
        CreateDebitNoteRequest request = sampleRequest();
        when(debitNoteService.createForSale(10L, request)).thenReturn(sampleDTO());

        ResponseEntity<DebitNoteDTO> response = controller.createForSale(10L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10L, response.getBody().saleId());
        verify(debitNoteService).createForSale(10L, request);
    }
}
