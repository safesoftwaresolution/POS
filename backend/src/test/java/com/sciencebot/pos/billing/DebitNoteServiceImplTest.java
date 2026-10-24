package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import com.sciencebot.pos.billing.internal.adapters.DebitNoteProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.ElectronicDebitNoteMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicDebitNoteRepository;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.internal.services.DebitNoteServiceImpl;
import com.sciencebot.pos.config.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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

        when(factusProvider.getProviderName()).thenReturn("factus");

        service = new DebitNoteServiceImpl(
                repository,
                new ElectronicDebitNoteMapper(),
                invoiceRepository,
                List.of(factusProvider)
        );
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
        when(repository.findByReferenceCodeForUpdate(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ElectronicDebitNote.class))).thenAnswer(i -> i.getArgument(0));
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
        verify(repository).save(any(ElectronicDebitNote.class));
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
    void createAndValidateDebitNote_WhenConflict409_AttemptsDeleteAndRetries() {
        CreateDebitNoteRequest request = createSampleRequest();
        stubNewNotePersistence();
        when(factusProvider.emitDebitNote(eq(request), eq(1L)))
                .thenReturn(DebitNoteResult.error("Conflicto DIAN/Factus (HTTP 409): documento pendiente"))
                .thenReturn(DebitNoteResult.success("ND990000002", "cude-retry", "qr", "pdf"));
        when(factusProvider.deleteUnvalidatedDebitNote(request.referenceCode(), 1L)).thenReturn(true);

        DebitNoteDTO result = service.createAndValidateDebitNote(request);

        assertEquals("ND990000002", result.debitNoteNumber());
        assertEquals("VALIDATED", result.status());
        verify(factusProvider).deleteUnvalidatedDebitNote(request.referenceCode(), 1L);
        verify(factusProvider, times(2)).emitDebitNote(eq(request), eq(1L));
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
    void deleteUnvalidatedDebitNote_WhenValidated_ThrowsIllegalState() {
        when(repository.findByReferenceCodeAndStoreId("ND-001", 1L)).thenReturn(Optional.of(validatedNote(1L, "ND-001")));

        assertThrows(IllegalStateException.class, () -> service.deleteUnvalidatedDebitNote("ND-001"));
        verify(factusProvider, never()).deleteUnvalidatedDebitNote(any(), any());
    }

    @Test
    void deleteUnvalidatedDebitNote_WhenNotValidated_DeletesFromProviderAndRepo() {
        ElectronicDebitNote unvalidated = ElectronicDebitNote.initialize(1L, null, "ND-001", "SETP-01", "1", "30");
        unvalidated.markAsError("Error DIAN");
        when(repository.findByReferenceCodeAndStoreId("ND-001", 1L)).thenReturn(Optional.of(unvalidated));
        when(factusProvider.deleteUnvalidatedDebitNote("ND-001", 1L)).thenReturn(true);

        assertTrue(service.deleteUnvalidatedDebitNote("ND-001"));
        verify(repository).delete(unvalidated);
    }

    @Test
    void deleteUnvalidatedDebitNote_WhenProviderFails_KeepsLocalRecord() {
        ElectronicDebitNote unvalidated = ElectronicDebitNote.initialize(1L, null, "ND-001", "SETP-01", "1", "30");
        unvalidated.markAsError("Error DIAN");
        when(repository.findByReferenceCodeAndStoreId("ND-001", 1L)).thenReturn(Optional.of(unvalidated));
        when(factusProvider.deleteUnvalidatedDebitNote("ND-001", 1L)).thenReturn(false);

        assertFalse(service.deleteUnvalidatedDebitNote("ND-001"));
        verify(repository, never()).delete(any());
    }
}
