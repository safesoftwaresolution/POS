package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.adapters.mock.MockBillingAdapter;
import com.sciencebot.pos.billing.internal.entities.ElectronicCreditNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;
import com.sciencebot.pos.billing.internal.repositories.ElectronicCreditNoteRepository;
import com.sciencebot.pos.billing.internal.repositories.ElectronicDebitNoteRepository;
import com.sciencebot.pos.billing.internal.services.CreditNoteService;
import com.sciencebot.pos.billing.internal.services.DebitNoteService;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Las notas credito y debito siguen la misma disciplina que las facturas: ninguna llamada al proveedor ocurre dentro
 * de una transaccion ni con el lock de la fila tomado, y emisiones simultaneas de la misma referencia llaman al
 * proveedor una sola vez. Hilos reales, sin transaccion de test.
 */
@SpringBootTest(properties = "billing.provider=mock")
class NoteEmissionConcurrencyTest {

    @Autowired
    private CreditNoteService creditNoteService;
    @Autowired
    private DebitNoteService debitNoteService;
    @Autowired
    private ElectronicCreditNoteRepository creditRepository;
    @Autowired
    private ElectronicDebitNoteRepository debitRepository;
    @MockitoSpyBean
    private MockBillingAdapter provider;

    private Long storeId;
    private String creditRef;
    private String debitRef;

    @BeforeEach
    void setUp() {
        long unique = System.nanoTime() % 1_000_000L;
        storeId = 600_000L + unique;
        creditRef = "NC-CONC-" + unique;
        debitRef = "ND-CONC-" + unique;
        TenantContext.setStoreId(storeId);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        creditRepository.findByReferenceCodeAndStoreId(creditRef, storeId).ifPresent(creditRepository::delete);
        debitRepository.findByReferenceCodeAndStoreId(debitRef, storeId).ifPresent(debitRepository::delete);
    }

    private CreateCreditNoteRequest creditRequest() {
        return new CreateCreditNoteRequest(creditRef, "2", "20", "SETP990002519", 1776, "Anulación",
                List.of(new CreateCreditNoteRequest.PaymentDetailRequest("1", "42", "pago-01", "83300.00")), null,
                List.of(new CreateCreditNoteRequest.ItemCreditNoteRequest(
                        "PROD-1", "Prod 1", "1.00", "0.00", "10000.00", "94", "999", List.of())));
    }

    private CreateDebitNoteRequest debitRequest() {
        return new CreateDebitNoteRequest(debitRef, "1", "30", "SETP990002519", 1776, "Intereses por mora",
                List.of(new CreateDebitNoteRequest.PaymentDetailRequest("1", "10", "pago-nd-01", null, "17850.00")), null,
                List.of(new CreateDebitNoteRequest.ItemDebitNoteRequest(
                        "INT-MORA", "Intereses por mora", "1.00", "0.00", "15000.00", "94", "999",
                        List.of(new CreateDebitNoteRequest.ItemDebitNoteRequest.TaxItemRequest("01", "19.00")))));
    }

    @Test
    void pendingCreditResponseRemainsPendingAndKeepsTheProviderId() {
        doReturn(CreditNoteResult.pending("Waiting for DIAN").withProviderDocumentId("credit-waiting"))
                .when(provider).emitCreditNote(any(), any());

        CreditNoteDTO result = creditNoteService.createAndValidateCreditNote(creditRequest());

        assertEquals("PENDING", result.status());
        ElectronicCreditNote persisted = creditRepository.findByReferenceCodeAndStoreId(creditRef, storeId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.PENDING, persisted.getStatus());
        assertEquals("credit-waiting", persisted.getProviderDocumentId());
    }

    @Test
    void pendingDebitResponseRemainsPendingAndKeepsTheProviderId() {
        doReturn(DebitNoteResult.pending("Waiting for DIAN").withProviderDocumentId("debit-waiting"))
                .when(provider).emitDebitNote(any(), any());

        DebitNoteDTO result = debitNoteService.createAndValidateDebitNote(debitRequest());

        assertEquals("PENDING", result.status());
        ElectronicDebitNote persisted = debitRepository.findByReferenceCodeAndStoreId(debitRef, storeId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.PENDING, persisted.getStatus());
        assertEquals("debit-waiting", persisted.getProviderDocumentId());
    }

    private <T> List<T> inParallel(int threads, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                TenantContext.setStoreId(storeId);
                try {
                    start.await();
                    return task.call();
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        start.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }

    @Test
    void theCreditNoteProviderIsCalledWithoutAnActiveTransaction() {
        AtomicBoolean transactionWasActive = new AtomicBoolean(true);
        doAnswer(invocation -> {
            transactionWasActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(provider).emitCreditNote(any(), any());

        CreditNoteDTO result = creditNoteService.createAndValidateCreditNote(creditRequest());

        assertEquals("VALIDATED", result.status());
        assertFalse(transactionWasActive.get(), "la llamada de red no puede ocurrir dentro de una transaccion");
    }

    @Test
    void theDebitNoteProviderIsCalledWithoutAnActiveTransaction() {
        AtomicBoolean transactionWasActive = new AtomicBoolean(true);
        doAnswer(invocation -> {
            transactionWasActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(provider).emitDebitNote(any(), any());

        DebitNoteDTO result = debitNoteService.createAndValidateDebitNote(debitRequest());

        assertEquals("VALIDATED", result.status());
        assertFalse(transactionWasActive.get(), "la llamada de red no puede ocurrir dentro de una transaccion");
    }

    @Test
    void tenParallelEmissionsOfTheSameCreditNoteCallTheProviderOnce() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(500);
            return invocation.callRealMethod();
        }).when(provider).emitCreditNote(any(), any());

        List<CreditNoteDTO> results = inParallel(10, () -> creditNoteService.createAndValidateCreditNote(creditRequest()));

        results.forEach(r -> assertNotNull(r));
        verify(provider, times(1)).emitCreditNote(any(), any());
        ElectronicCreditNote stored = creditRepository.findByReferenceCodeAndStoreId(creditRef, storeId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored.getStatus());
        assertEquals(1, stored.getAttempts());
        assertNull(stored.getProcessingStartedAt());
    }

    @Test
    void tenParallelEmissionsOfTheSameDebitNoteCallTheProviderOnce() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(500);
            return invocation.callRealMethod();
        }).when(provider).emitDebitNote(any(), any());

        List<DebitNoteDTO> results = inParallel(10, () -> debitNoteService.createAndValidateDebitNote(debitRequest()));

        results.forEach(r -> assertNotNull(r));
        verify(provider, times(1)).emitDebitNote(any(), any());
        ElectronicDebitNote stored = debitRepository.findByReferenceCodeAndStoreId(debitRef, storeId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored.getStatus());
        assertEquals(1, stored.getAttempts());
        assertNull(stored.getProcessingStartedAt());
    }

    @Test
    void aProviderErrorLeavesTheNoteInErrorAndReleasesTheReservation() {
        doReturn(CreditNoteResult.error("timeout")).when(provider).emitCreditNote(any(), any());
        doReturn(DebitNoteResult.error("timeout")).when(provider).emitDebitNote(any(), any());

        creditNoteService.createAndValidateCreditNote(creditRequest());
        debitNoteService.createAndValidateDebitNote(debitRequest());

        ElectronicCreditNote credit = creditRepository.findByReferenceCodeAndStoreId(creditRef, storeId).orElseThrow();
        ElectronicDebitNote debit = debitRepository.findByReferenceCodeAndStoreId(debitRef, storeId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.ERROR, credit.getStatus());
        assertEquals(ElectronicInvoiceStatus.ERROR, debit.getStatus());
        assertNull(credit.getProcessingStartedAt());
        assertNull(debit.getProcessingStartedAt());
        assertEquals(1, credit.getAttempts());
        assertEquals(1, debit.getAttempts());
    }

    @Test
    void aFailedNoteCanBeEmittedAgainAndKeepsCountingAttempts() {
        doReturn(CreditNoteResult.error("timeout")).when(provider).emitCreditNote(any(), any());
        creditNoteService.createAndValidateCreditNote(creditRequest());
        doCallRealMethod().when(provider).emitCreditNote(any(), any());

        CreditNoteDTO second = creditNoteService.createAndValidateCreditNote(creditRequest());

        assertEquals("VALIDATED", second.status());
        verify(provider, times(2)).emitCreditNote(any(), any());
    }

    @Test
    void aReservationThatExpiredIsTakenOverByTheNextCaller() {
        // Un hilo murio con la reserva tomada: pasado el lease, la siguiente emision la retoma.
        creditRepository.saveAndFlush(reservedLongAgo());

        CreditNoteDTO result = creditNoteService.createAndValidateCreditNote(creditRequest());

        assertEquals("VALIDATED", result.status());
        verify(provider, times(1)).emitCreditNote(any(), any());
    }

    private ElectronicCreditNote reservedLongAgo() {
        ElectronicCreditNote note = ElectronicCreditNote.initialize(storeId, null, creditRef, "SETP990002519", "2", "20");
        note.reserveForEmission(LocalDateTime.now().minusHours(2), java.time.Duration.ofMinutes(5));
        return note;
    }
}
