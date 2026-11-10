package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import com.sciencebot.pos.billing.internal.adapters.DebitNoteProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.mappers.ElectronicDebitNoteMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicDebitNoteRepository;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.internal.services.DebitNoteEmissionTransactions;
import com.sciencebot.pos.billing.internal.services.DebitNoteServiceImpl;
import com.sciencebot.pos.config.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DebitNoteServiceImplTest {

    @Mock
    private ElectronicDebitNoteRepository repository;

    @Mock
    private ElectronicInvoiceRepository invoiceRepository;

    @Mock
    private DebitNoteProvider factusProvider;

    private DebitNoteServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setStoreId(1L);

        when(factusProvider.getProviderName()).thenReturn("alegra");
        doAnswer(invocation -> factusProvider.emitDebitNote(invocation.getArgument(0), invocation.getArgument(1)))
                .when(factusProvider).emitDebitNote(any(), any(), any(), any());
        ElectronicInvoice source = ElectronicInvoice.initialize(10L, 1L);
        source.assignDocumentContext(new BillingDocumentContext(1L, BillingEnvironment.SANDBOX, "alegra", "original-company"));
        source.markAsValidated("SETP990002519", "cufe", "qr", null, LocalDateTime.now());
        when(invoiceRepository.findByStoreIdAndLegalNumber(1L, "SETP990002519")).thenReturn(Optional.of(source));

        ElectronicDebitNoteMapper mapper = new ElectronicDebitNoteMapper();
        service = new DebitNoteServiceImpl(
                repository,
                mapper,
                invoiceRepository,
                new DebitNoteEmissionTransactions(repository, mapper, Duration.ofMinutes(5), invoiceRepository),
                List.of(factusProvider)
        );
    }

    /** La nota se crea en la reserva y se vuelve a leer al registrar el resultado: un repositorio con estado minimo. */
    private void givenTheNoteIsStoredOnceCreated() {
        java.util.concurrent.atomic.AtomicReference<ElectronicDebitNote> stored = new java.util.concurrent.atomic.AtomicReference<>();
        when(repository.findByReferenceCodeForUpdate(any())).thenAnswer(i -> Optional.ofNullable(stored.get()));
        when(repository.saveAndFlush(any(ElectronicDebitNote.class))).thenAnswer(i -> {
            stored.set(i.getArgument(0));
            return i.getArgument(0);
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CreateDebitNoteRequest createSampleRequest() {
        return createRequest("ND-2026-0001", "30", "SETP990002519");
    }

    private CreateDebitNoteRequest createRequest(String referenceCode, String customizationId, String billNumber) {
        return new CreateDebitNoteRequest(
                referenceCode,
                "1",
                customizationId,
                billNumber,
                1776,
                "Intereses por mora",
                List.of(new CreateDebitNoteRequest.PaymentDetailRequest("1", "10", "pago-nd-01", null, "17850.00")),
                null,
                List.of(new CreateDebitNoteRequest.ItemDebitNoteRequest(
                        "INT-MORA", "Intereses por mora", "1.00", "0.00", "15000.00", "94", "999",
                        List.of(new CreateDebitNoteRequest.ItemDebitNoteRequest.TaxItemRequest("01", "19.00"))
                ))
        );
    }

    private void stubNewNotePersistence() {
        givenTheNoteIsStoredOnceCreated();
        when(repository.save(any(ElectronicDebitNote.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ElectronicDebitNote validatedNote(Long storeId, String referenceCode) {
        ElectronicDebitNote note = ElectronicDebitNote.initialize(storeId, null, referenceCode, "SETP990002519", "1", "30");
        note.setId(7L);
        note.markAsValidated("ND990000001", "cude-abc", "qr", "pdf", LocalDateTime.now());
        return note;
    }

    // --- Emisión ---

    @Test
    void createAndValidateDebitNote_SuccessfulEmission_SavesAndReturnsValidatedDto() {
        CreateDebitNoteRequest request = createSampleRequest();
        stubNewNotePersistence();
        when(factusProvider.emitDebitNote(eq(request), eq(1L)))
                .thenReturn(DebitNoteResult.success("ND990000001", "cude-abc", "qr-url", "pdf-url"));

        DebitNoteDTO result = service.createAndValidateDebitNote(request);

        assertEquals("ND990000001", result.debitNoteNumber());
        assertEquals("VALIDATED", result.status());
        assertEquals("cude-abc", result.cude());
        assertEquals(1L, result.storeId());
        assertNull(result.saleId());
        verify(repository, times(2)).save(any(ElectronicDebitNote.class)); // reserva + resultado
    }

    @Test
    void createAndValidateDebitNote_Idempotency_WhenAlreadyValidated_ReturnsExistingWithoutCallingProvider() {
        CreateDebitNoteRequest request = createSampleRequest();
        when(repository.findByReferenceCodeForUpdate(request.referenceCode()))
                .thenReturn(Optional.of(validatedNote(1L, request.referenceCode())));

        DebitNoteDTO result = service.createAndValidateDebitNote(request);

        assertEquals("VALIDATED", result.status());
        assertEquals("ND990000001", result.debitNoteNumber());
        verify(factusProvider, never()).emitDebitNote(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void createAndValidateDebitNote_RetryAfterError_ReusesExistingNote() {
        CreateDebitNoteRequest request = createSampleRequest();
        ElectronicDebitNote failed = ElectronicDebitNote.initialize(1L, null, request.referenceCode(), "SETP990002519", "1", "30");
        failed.markAsError("Factus no disponible");
        when(repository.findByReferenceCodeForUpdate(request.referenceCode())).thenReturn(Optional.of(failed));
        when(repository.save(any(ElectronicDebitNote.class))).thenAnswer(i -> i.getArgument(0));
        when(factusProvider.emitDebitNote(eq(request), eq(1L)))
                .thenReturn(DebitNoteResult.success("ND990000002", "cude-2", "qr", "pdf"));

        DebitNoteDTO result = service.createAndValidateDebitNote(request);

        assertEquals("VALIDATED", result.status());
        assertNull(result.errorMessage());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createAndValidateDebitNote_WhenDianRejects_PersistsRejectedWithReason() {
        CreateDebitNoteRequest request = createSampleRequest();
        stubNewNotePersistence();
        when(factusProvider.emitDebitNote(eq(request), eq(1L)))
                .thenReturn(DebitNoteResult.rejected("Error Validación DIAN (HTTP 422): total inválido"));

        DebitNoteDTO result = service.createAndValidateDebitNote(request);

        assertEquals("REJECTED", result.status());
        assertTrue(result.errorMessage().contains("422"));
    }

    @Test
    void createAndValidateDebitNote_WhenProviderValidatesWithoutNumber_MarksErrorInsteadOfFailing() {
        CreateDebitNoteRequest request = createSampleRequest();
        stubNewNotePersistence();
        when(factusProvider.emitDebitNote(eq(request), eq(1L)))
                .thenReturn(DebitNoteResult.success(null, "cude", "qr", "pdf"));

        DebitNoteDTO result = service.createAndValidateDebitNote(request);

        assertEquals("ERROR", result.status());
        assertNull(result.debitNoteNumber());
        assertNotNull(result.errorMessage());
    }

    @Test
    void createAndValidateDebitNote_WhenProviderThrows_PersistsError() {
        CreateDebitNoteRequest request = createSampleRequest();
        stubNewNotePersistence();
        when(factusProvider.emitDebitNote(eq(request), eq(1L))).thenThrow(new RuntimeException("boom"));

        DebitNoteDTO result = service.createAndValidateDebitNote(request);

        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("boom"));
    }

    @Test
    void createAndValidateDebitNote_ReferenceOwnedByAnotherStore_ThrowsWithoutCallingProvider() {
        CreateDebitNoteRequest request = createSampleRequest();
        when(repository.findByReferenceCodeForUpdate(request.referenceCode()))
                .thenReturn(Optional.of(validatedNote(2L, request.referenceCode())));

        assertThrows(IllegalStateException.class, () -> service.createAndValidateDebitNote(request));
        verify(factusProvider, never()).emitDebitNote(any(), any());
    }

    @Test
    void createAndValidateDebitNote_ReferenceUsedForAnotherInvoice_Throws() {
        CreateDebitNoteRequest request = createRequest("ND-2026-0001", "30", "SETP-OTRA");
        when(repository.findByReferenceCodeForUpdate(request.referenceCode()))
                .thenReturn(Optional.of(validatedNote(1L, request.referenceCode())));

        assertThrows(IllegalStateException.class, () -> service.createAndValidateDebitNote(request));
        verify(factusProvider, never()).emitDebitNote(any(), any());
    }

    @Test
    void createAndValidateDebitNote_WithoutInvoiceReference_ThrowsIllegalArgument() {
        CreateDebitNoteRequest request = createRequest("ND-2026-0001", "32", null);

        assertThrows(IllegalArgumentException.class, () -> service.createAndValidateDebitNote(request));
        verifyNoInteractions(repository);
    }

    @Test
    void createAndValidateDebitNote_WithoutBillNumber_ThrowsIllegalArgument() {
        CreateDebitNoteRequest request = createRequest("ND-2026-0001", "30", " ");

        assertThrows(IllegalArgumentException.class, () -> service.createAndValidateDebitNote(request));
        verifyNoInteractions(repository);
    }

    @Test
    void createAndValidateDebitNote_WithoutItems_ThrowsIllegalArgument() {
        CreateDebitNoteRequest request = new CreateDebitNoteRequest(
                "ND-2026-0001", "1", "30", "SETP990002519", 1776, null, List.of(), null, List.of());

        assertThrows(IllegalArgumentException.class, () -> service.createAndValidateDebitNote(request));
        verifyNoInteractions(repository);
    }

    @Test
    void createAndValidateDebitNote_WithoutTenant_ThrowsIllegalState() {
        TenantContext.clear();

        assertThrows(IllegalStateException.class, () -> service.createAndValidateDebitNote(createSampleRequest()));
        verifyNoInteractions(repository);
        verify(factusProvider, never()).emitDebitNote(any(), any());
    }

    // --- Emisión a partir de una venta ---

    @Test
    void createForSale_WhenInvoiceValidated_BindsBillNumberAndSale() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(10L, 1L);
        invoice.assignDocumentContext(new BillingDocumentContext(1L, BillingEnvironment.SANDBOX, "alegra", "original-company"));
        invoice.markAsValidated("SETP-001", "cufe-123", "qr", "pdf", LocalDateTime.now());
        when(invoiceRepository.findBySaleIdAndStoreId(10L, 1L)).thenReturn(Optional.of(invoice));
        stubNewNotePersistence();
        when(factusProvider.emitDebitNote(any(), eq(1L)))
                .thenReturn(DebitNoteResult.success("ND-888", "cude-888", "qr", "pdf"));

        DebitNoteDTO result = service.createForSale(10L, createRequest("ND-VENTA-10", null, null));

        assertEquals("ND-888", result.debitNoteNumber());
        assertEquals("SETP-001", result.billNumber());
        assertEquals(10L, result.saleId());

        ArgumentCaptor<CreateDebitNoteRequest> sent = ArgumentCaptor.forClass(CreateDebitNoteRequest.class);
        verify(factusProvider).emitDebitNote(sent.capture(), eq(1L));
        assertEquals("SETP-001", sent.getValue().billNumber());
        assertEquals("30", sent.getValue().customizationId());
    }

    @Test
    void createForSale_WhenInvoiceNotValidated_ThrowsIllegalState() {
        when(invoiceRepository.findBySaleIdAndStoreId(10L, 1L)).thenReturn(Optional.of(ElectronicInvoice.initialize(10L, 1L)));

        assertThrows(IllegalStateException.class, () -> service.createForSale(10L, createSampleRequest()));
        verify(factusProvider, never()).emitDebitNote(any(), any());
    }

    @Test
    void createForSale_WhenSaleHasNoInvoiceInStore_ThrowsNotFound() {
        when(invoiceRepository.findBySaleIdAndStoreId(10L, 1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.createForSale(10L, createSampleRequest()));
    }

    @Test
    void createForSale_WhenBillNumberDoesNotMatchSaleInvoice_ThrowsIllegalArgument() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(10L, 1L);
        invoice.assignDocumentContext(new BillingDocumentContext(1L, BillingEnvironment.SANDBOX, "alegra", "original-company"));
        invoice.markAsValidated("SETP-001", "cufe-123", "qr", "pdf", LocalDateTime.now());
        when(invoiceRepository.findBySaleIdAndStoreId(10L, 1L)).thenReturn(Optional.of(invoice));

        assertThrows(IllegalArgumentException.class,
                () -> service.createForSale(10L, createRequest("ND-VENTA-10", "30", "SETP-OTRA")));
        verify(factusProvider, never()).emitDebitNote(any(), any());
    }

    // --- Consultas y descargas ---

    @Test
    void getById_NoteFromAnotherStore_ReturnsEmpty() {
        when(repository.findById(7L)).thenReturn(Optional.of(validatedNote(2L, "ND-OTRO")));

        assertTrue(service.getById(7L).isEmpty());
    }

    @Test
    void getByReferenceCode_QueriesOnlyCurrentStore() {
        when(repository.findByReferenceCodeAndStoreId("ND-2026-0001", 1L))
                .thenReturn(Optional.of(validatedNote(1L, "ND-2026-0001")));

        Optional<DebitNoteDTO> result = service.getByReferenceCode(" ND-2026-0001 ");

        assertTrue(result.isPresent());
        assertEquals("ND990000001", result.get().debitNoteNumber());
    }

    @Test
    void downloadDebitNotePDF_WhenValidated_ReturnsProviderBytes() {
        byte[] pdf = "%PDF".getBytes();
        when(repository.findById(7L)).thenReturn(Optional.of(validatedNote(1L, "ND-2026-0001")));
        when(factusProvider.downloadDebitNotePDF("ND990000001", 1L)).thenReturn(pdf);

        assertArrayEquals(pdf, service.downloadDebitNotePDF(7L));
    }

    @Test
    void downloadDebitNoteXML_WhenNotValidated_ThrowsIllegalState() {
        ElectronicDebitNote pending = ElectronicDebitNote.initialize(1L, null, "ND-2026-0001", "SETP990002519", "1", "30");
        when(repository.findById(7L)).thenReturn(Optional.of(pending));

        assertThrows(IllegalStateException.class, () -> service.downloadDebitNoteXML(7L));
        verify(factusProvider, never()).downloadDebitNoteXML(any(), any());
    }

    @Test
    void downloadDebitNotePDF_NoteFromAnotherStore_ThrowsNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.of(validatedNote(2L, "ND-OTRO")));

        assertThrows(EntityNotFoundException.class, () -> service.downloadDebitNotePDF(7L));
    }

    // --- Eliminación de borradores ---


    @Test
    void createAndValidateDebitNote_ProviderOfTheOriginalContextIsGone_EndsInErrorInsteadOfStayingReserved() {
        ElectronicInvoice legacy = ElectronicInvoice.initialize(10L, 1L);
        legacy.assignDocumentContext(new BillingDocumentContext(1L, BillingEnvironment.SANDBOX, "factus", null));
        legacy.markAsValidated("SETP990002519", "cufe", "qr", null, LocalDateTime.now());
        when(invoiceRepository.findByStoreIdAndLegalNumber(1L, "SETP990002519")).thenReturn(Optional.of(legacy));
        stubNewNotePersistence();

        DebitNoteDTO result = service.createAndValidateDebitNote(createSampleRequest());

        assertEquals("ERROR", result.status());
        assertTrue(result.errorMessage().contains("factus"), result.errorMessage());
        verify(factusProvider, never()).emitDebitNote(any(), any());
    }

    private void storedFailedNote(String providerName, String companyId) {
        ElectronicDebitNote note = ElectronicDebitNote.initialize(1L, null, "ND-2026-0001", "SETP990002519", "1", "30");
        note.setId(7L);
        note.assignDocumentContext(new BillingDocumentContext(1L, BillingEnvironment.SANDBOX, providerName, companyId));
        note.markAsError("timeout");
        when(repository.findById(7L)).thenReturn(Optional.of(note));
        when(repository.findByReferenceCodeForUpdate("ND-2026-0001")).thenReturn(Optional.of(note));
        when(repository.save(any(ElectronicDebitNote.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void retry_ReplaysTheStoredNoteWithItsOriginalContextAndRecordsTheResult() {
        storedFailedNote("alegra", "original-company");
        BillingDocumentContext original = new BillingDocumentContext(1L, BillingEnvironment.SANDBOX, "alegra", "original-company");
        when(factusProvider.retryDebitNote("ND-2026-0001", original))
                .thenReturn(DebitNoteResult.success("ND990000009", "cude-r", "qr", "pdf"));

        DebitNoteDTO result = service.retry(7L);

        assertEquals("VALIDATED", result.status());
        assertEquals("ND990000009", result.debitNoteNumber());
        verify(factusProvider).retryDebitNote("ND-2026-0001", original);
    }

    @Test
    void retry_ProviderOfTheOriginalContextIsGone_RecordsItInsteadOfThrowing() {
        storedFailedNote("factus", null);

        DebitNoteDTO result = assertDoesNotThrow(() -> service.retry(7L));

        assertNotEquals("VALIDATED", result.status());
        assertTrue(result.errorMessage().contains("factus"), result.errorMessage());
    }
}
