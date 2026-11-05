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
import com.sciencebot.pos.billing.internal.services.CreditNoteEmissionTransactions;
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
import java.time.Duration;
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

        when(factusProvider.getProviderName()).thenReturn("alegra");

        ElectronicCreditNoteMapper mapper = new ElectronicCreditNoteMapper();
        service = new CreditNoteServiceImpl(
                repository,
                mapper,
                invoiceRepository,
                saleFacade,
                new CreditNoteEmissionTransactions(repository, mapper, Duration.ofMinutes(5)),
                List.of(factusProvider)
        );
    }

    /** La nota se crea en la reserva y se vuelve a leer al registrar el resultado: un repositorio con estado minimo. */
    private void givenTheNoteIsStoredOnceCreated() {
        java.util.concurrent.atomic.AtomicReference<ElectronicCreditNote> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(repository.findByReferenceCodeForUpdate(any())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(repository.saveAndFlush(any(ElectronicCreditNote.class))).thenAnswer(i -> {
            stored.set(i.getArgument(0));
            return i.getArgument(0);
        });
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

        givenTheNoteIsStoredOnceCreated();

        ElectronicCreditNote initialNote = ElectronicCreditNote.initialize(1L, null, request.referenceCode(), request.billNumber(), "2", "20");
        when(repository.save(any(ElectronicCreditNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreditNoteResult successResult = CreditNoteResult.success("NC990000001", "cude-abc", "qr-url", "pdf-url");
        when(factusProvider.emitCreditNote(eq(request), eq(1L))).thenReturn(successResult);

        CreditNoteDTO result = service.createAndValidateCreditNote(request);

        assertNotNull(result);
        assertEquals("NC990000001", result.creditNoteNumber());
        assertEquals("VALIDATED", result.status());
        assertEquals("cude-abc", result.cude());
        verify(repository, times(2)).save(any(ElectronicCreditNote.class)); // reserva + resultado
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

        givenTheNoteIsStoredOnceCreated();
        when(repository.save(any(ElectronicCreditNote.class))).thenAnswer(i -> i.getArgument(0));

        CreditNoteResult successResult = CreditNoteResult.success("NC-888", "cude-888", "qr", "pdf");
        when(factusProvider.emitCreditNote(any(), eq(1L))).thenReturn(successResult);

        CreditNoteDTO result = service.createFromSale(10L, "2", "Anulación venta");

        assertNotNull(result);
        assertEquals("NC-888", result.creditNoteNumber());
        assertEquals("VALIDATED", result.status());
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
        givenTheNoteIsStoredOnceCreated();
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
    }
}