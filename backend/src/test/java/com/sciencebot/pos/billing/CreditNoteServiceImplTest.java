package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import com.sciencebot.pos.billing.internal.adapters.CreditNoteProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.entities.ElectronicCreditNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.ElectronicCreditNoteMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicCreditNoteRepository;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.internal.services.CreditNoteServiceImpl;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CreditNoteServiceImplTest {

    @Mock
    private ElectronicCreditNoteRepository repository;

    @Mock
    private ElectronicInvoiceRepository invoiceRepository;

    @Mock
    private SaleFacade saleFacade;

    @Mock
    private CreditNoteProvider factusProvider;

    private CreditNoteServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setStoreId(1L);

        when(factusProvider.getProviderName()).thenReturn("factus");

        service = new CreditNoteServiceImpl(
                repository,
                new ElectronicCreditNoteMapper(),
                invoiceRepository,
                saleFacade,
                List.of(factusProvider)
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CreateCreditNoteRequest createSampleRequest() {
        return new CreateCreditNoteRequest(
                "NOTA-2026-0015",
                "2",
                "20",
                "SETP990002519",
                1776,
                "Anulación",
                List.of(new CreateCreditNoteRequest.PaymentDetailRequest("1", "42", "pago-01", "83300.00")),
                null,
                List.of(new CreateCreditNoteRequest.ItemCreditNoteRequest(
                        "PROD-1", "Prod 1", "1.00", "0.00", "10000.00", "94", "999", List.of()
                ))
        );
    }

    @Test
    void createAndValidateCreditNote_SuccessfulEmission_SavesAndReturnsValidatedDto() {
        CreateCreditNoteRequest request = createSampleRequest();

        when(repository.findByReferenceCodeForUpdate(request.referenceCode())).thenReturn(Optional.empty());

        ElectronicCreditNote initialNote = ElectronicCreditNote.initialize(1L, null, request.referenceCode(), request.billNumber(), "2", "20");
        when(repository.saveAndFlush(any(ElectronicCreditNote.class))).thenReturn(initialNote);
        when(repository.save(any(ElectronicCreditNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditNoteResult successResult = CreditNoteResult.success("NC990000001", "cude-abc", "qr-url", "pdf-url");
        when(factusProvider.emitCreditNote(eq(request), eq(1L))).thenReturn(successResult);

        CreditNoteDTO result = service.createAndValidateCreditNote(request);

        assertNotNull(result);
        assertEquals("NC990000001", result.creditNoteNumber());
        assertEquals("VALIDATED", result.status());
        assertEquals("cude-abc", result.cude());
        verify(repository).save(any(ElectronicCreditNote.class));
        verify(factusProvider).emitCreditNote(eq(request), eq(1L));
    }

    @Test
    void createAndValidateCreditNote_Idempotency_WhenAlreadyValidated_ReturnsExistingWithoutCallingProvider() {
        CreateCreditNoteRequest request = createSampleRequest();

        ElectronicCreditNote validatedNote = ElectronicCreditNote.initialize(1L, null, request.referenceCode(), request.billNumber(), "2", "20");
        validatedNote.markAsValidated("NC990000001", "cude-abc", "qr", "pdf", LocalDateTime.now());

        when(repository.findByReferenceCodeForUpdate(request.referenceCode())).thenReturn(Optional.of(validatedNote));

        CreditNoteDTO result = service.createAndValidateCreditNote(request);

        assertNotNull(result);
        assertEquals("VALIDATED", result.status());
        assertEquals("NC990000001", result.creditNoteNumber());
        verify(factusProvider, never()).emitCreditNote(any(), any());
    }

    @Test
    void createAndValidateCreditNote_WhenConflict409_AttemptsDeleteAndRetries() {
        CreateCreditNoteRequest request = createSampleRequest();

        when(repository.findByReferenceCodeForUpdate(request.referenceCode())).thenReturn(Optional.empty());

        ElectronicCreditNote initialNote = ElectronicCreditNote.initialize(1L, null, request.referenceCode(), request.billNumber(), "2", "20");
        when(repository.saveAndFlush(any(ElectronicCreditNote.class))).thenReturn(initialNote);
        when(repository.save(any(ElectronicCreditNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditNoteResult conflictResult = CreditNoteResult.error("Error 409 Conflict: documento pendiente");
        CreditNoteResult retrySuccess = CreditNoteResult.success("NC990000002", "cude-retry", "qr", "pdf");

        when(factusProvider.emitCreditNote(eq(request), eq(1L)))
                .thenReturn(conflictResult)
                .thenReturn(retrySuccess);
        when(factusProvider.deleteUnvalidatedCreditNote(eq(request.referenceCode()), eq(1L))).thenReturn(true);

        CreditNoteDTO result = service.createAndValidateCreditNote(request);

        assertNotNull(result);
        assertEquals("NC990000002", result.creditNoteNumber());
        assertEquals("VALIDATED", result.status());
        verify(factusProvider).deleteUnvalidatedCreditNote(eq(request.referenceCode()), eq(1L));
        verify(factusProvider, times(2)).emitCreditNote(eq(request), eq(1L));
    }

    @Test
    void createFromSale_WhenInvoiceNotValidated_ThrowsIllegalStateException() {
        ElectronicInvoice pendingInvoice = ElectronicInvoice.initialize(10L, 1L);
        when(invoiceRepository.findBySaleIdAndStoreId(10L, 1L)).thenReturn(Optional.of(pendingInvoice));

        assertThrows(IllegalStateException.class, () -> service.createFromSale(10L, "2", "anulación"));
    }

    @Test
    void createFromSale_WhenInvoiceValidated_BuildsAndEmitsCreditNote() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(10L, 1L);
        invoice.markAsValidated("SETP-001", "cufe-123", "qr", "pdf", LocalDateTime.now());
        when(invoiceRepository.findBySaleIdAndStoreId(10L, 1L)).thenReturn(Optional.of(invoice));

        SaleDto sale = new SaleDto(10L, "FACT-001", LocalDateTime.now(), "Cliente", BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.ZERO, "cajero", List.of(), "CASH");
        when(saleFacade.getById(10L)).thenReturn(Optional.of(sale));

        when(repository.findByReferenceCodeForUpdate(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ElectronicCreditNote.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(ElectronicCreditNote.class))).thenAnswer(i -> i.getArgument(0));

        CreditNoteResult successResult = CreditNoteResult.success("NC-888", "cude-888", "qr", "pdf");
        when(factusProvider.emitCreditNote(any(), eq(1L))).thenReturn(successResult);

        CreditNoteDTO result = service.createFromSale(10L, "2", "Anulación venta");

        assertNotNull(result);
        assertEquals("NC-888", result.creditNoteNumber());
        assertEquals("VALIDATED", result.status());
    }

    @Test
    void deleteUnvalidatedCreditNote_WhenValidated_ThrowsIllegalStateException() {
        ElectronicCreditNote validated = ElectronicCreditNote.initialize(1L, null, "NOTA-001", "SETP-01", "2", "20");
        validated.markAsValidated("NC-01", "cude", "qr", "pdf", LocalDateTime.now());

        when(repository.findByReferenceCodeAndStoreId("NOTA-001", 1L)).thenReturn(Optional.of(validated));

        assertThrows(IllegalStateException.class, () -> service.deleteUnvalidatedCreditNote("NOTA-001"));
    }

    @Test
    void deleteUnvalidatedCreditNote_WhenNotValidated_DeletesFromProviderAndRepo() {
        ElectronicCreditNote unvalidated = ElectronicCreditNote.initialize(1L, null, "NOTA-001", "SETP-01", "2", "20");
        unvalidated.markAsError("Error DIAN");

        when(repository.findByReferenceCodeAndStoreId("NOTA-001", 1L)).thenReturn(Optional.of(unvalidated));
        when(factusProvider.deleteUnvalidatedCreditNote("NOTA-001", 1L)).thenReturn(true);

        boolean deleted = service.deleteUnvalidatedCreditNote("NOTA-001");

        assertTrue(deleted);
        verify(factusProvider).deleteUnvalidatedCreditNote("NOTA-001", 1L);
        verify(repository).delete(unvalidated);
    }

    @Test
    void createAndValidateCreditNote_ReferenceOwnedByAnotherStore_IsRefusedWithoutLeakingIt() {
        CreateCreditNoteRequest request = createSampleRequest();
        ElectronicCreditNote otherStoresNote = ElectronicCreditNote.initialize(2L, null, request.referenceCode(), request.billNumber(), "2", "20");
        otherStoresNote.markAsValidated("NC-AJENA", "cude", "qr", "pdf", LocalDateTime.now());
        when(repository.findByReferenceCodeForUpdate(request.referenceCode())).thenReturn(Optional.of(otherStoresNote));

        assertThrows(IllegalStateException.class, () -> service.createAndValidateCreditNote(request));

        verify(factusProvider, never()).emitCreditNote(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void createAndValidateCreditNote_WithoutStore_RefusesInsteadOfUsingStoreOne() {
        TenantContext.clear();

        assertThrows(IllegalStateException.class, () -> service.createAndValidateCreditNote(createSampleRequest()));

        verify(repository, never()).saveAndFlush(any());
        verify(factusProvider, never()).emitCreditNote(any(), any());
    }

    @Test
    void createFromSale_UsesDeterministicReferenceAndPersistsSaleId() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(10L, 1L);
        invoice.markAsValidated("SETP-001", "cufe-123", "qr", "pdf", LocalDateTime.now());
        when(invoiceRepository.findBySaleIdAndStoreId(10L, 1L)).thenReturn(Optional.of(invoice));
        SaleDto sale = new SaleDto(10L, "FACT-001", LocalDateTime.now(), "Cliente", BigDecimal.valueOf(100), BigDecimal.valueOf(100), BigDecimal.ZERO, "cajero", List.of(), "CASH");
        when(saleFacade.getById(10L)).thenReturn(Optional.of(sale));
        when(repository.findByReferenceCodeForUpdate(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ElectronicCreditNote.class))).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any(ElectronicCreditNote.class))).thenAnswer(i -> i.getArgument(0));
        when(factusProvider.emitCreditNote(any(), eq(1L))).thenReturn(CreditNoteResult.success("NC-1", "cude", "qr", "pdf"));

        CreditNoteDTO first = service.createFromSale(10L, "2", "obs");
        CreditNoteDTO second = service.createFromSale(10L, "2", "obs");

        assertEquals("NC-FACT-001-C2", first.referenceCode());
        assertEquals(first.referenceCode(), second.referenceCode());
        assertEquals(10L, first.saleId());
    }

    @Test
    void getById_AndDownloads_WithoutStore_AreRefused() {
        TenantContext.clear();

        assertTrue(service.getById(1L).isEmpty());
        assertThrows(IllegalStateException.class, () -> service.downloadCreditNotePDF(1L));
        assertThrows(IllegalStateException.class, () -> service.deleteUnvalidatedCreditNote("NOTA-001"));
    }
}