package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceDto;
import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.billing.internal.adapters.mock.MockBillingAdapter;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.mappers.BillingCanonicalMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.internal.services.InvoiceService;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Ninguna llamada al proveedor ocurre dentro de una transaccion ni con el lock de la fila tomado, y dos
 * emisiones simultaneas de la misma venta llaman al proveedor una sola vez. Hilos reales, sin transaccion de test.
 */
@SpringBootTest(properties = "billing.provider=mock")
class InvoiceEmissionConcurrencyTest {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private ElectronicInvoiceRepository repository;
    @MockitoSpyBean
    private MockBillingAdapter provider;
    @MockitoBean
    private BillingCanonicalMapper canonicalMapper;
    @MockitoBean
    private SaleFacade saleFacade;

    private Long storeId;
    private Long saleId;

    @BeforeEach
    void setUp() {
        storeId = 400_000L + (System.nanoTime() % 1_000_000L);
        saleId = 500_000L + (System.nanoTime() % 1_000_000L);
        when(canonicalMapper.toInvoiceRequest(any())).thenReturn(mock(InvoiceRequest.class));
        when(saleFacade.getById(saleId)).thenAnswer(inv -> Optional.of(sale()));
        when(saleFacade.restrictedSellerId()).thenReturn(Optional.empty());
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        repository.findBySaleId(saleId).ifPresent(repository::delete);
    }

    private SaleDto sale() {
        return new SaleDto(saleId, storeId, null, "FACT-" + saleId, null, "Cliente", BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.ZERO, "admin", List.of(), "CASH");
    }

    @Test
    void theProviderIsCalledWithoutAnActiveTransaction() {
        AtomicBoolean transactionWasActive = new AtomicBoolean(true);
        doAnswer(invocation -> {
            transactionWasActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(provider).emitInvoice(any());

        ElectronicInvoiceDto result = invoiceService.processElectronicInvoice(sale());

        assertEquals("VALIDATED", result.status());
        assertFalse(transactionWasActive.get(), "la llamada de red no puede ocurrir dentro de una transaccion");
    }

    @Test
    void tenParallelEmissionsOfTheSameSaleCallTheProviderOnce() throws Exception {
        // Proveedor lento: los demas hilos llegan mientras la primera emision sigue en curso (la reserva vigente).
        doAnswer(invocation -> {
            Thread.sleep(500);
            return invocation.callRealMethod();
        }).when(provider).emitInvoice(any());
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ElectronicInvoiceDto>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                TenantContext.setStoreId(storeId);
                try {
                    start.await();
                    return invoiceService.processElectronicInvoice(sale());
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        start.countDown();
        for (Future<ElectronicInvoiceDto> future : futures) {
            assertNotNull(future.get());
        }
        pool.shutdown();

        verify(provider, times(1)).emitInvoice(any());
        ElectronicInvoice stored = repository.findBySaleId(saleId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored.getStatus());
        assertEquals(1, stored.getAttempts());
        assertNull(stored.getProcessingStartedAt());
    }

    @Test
    void aProviderErrorLeavesTheInvoiceInErrorWithARetryScheduled() {
        doReturn(InvoiceResult.error("timeout")).when(provider).emitInvoice(any());

        invoiceService.processElectronicInvoice(sale());

        ElectronicInvoice stored = repository.findBySaleId(saleId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.ERROR, stored.getStatus());
        assertEquals(1, stored.getAttempts());
        assertNotNull(stored.getNextRetryAt());
        assertTrue(stored.getNextRetryAt().isAfter(LocalDateTime.now()));
        assertNull(stored.getProcessingStartedAt());
    }

    @Test
    void aPendingAnswerStaysPendingAndAnExplicitRejectionIsNeverRetriedAutomatically() {
        doReturn(InvoiceResult.pending("La DIAN aún no responde")).when(provider).emitInvoice(any());
        invoiceService.processElectronicInvoice(sale());
        ElectronicInvoice pending = repository.findBySaleId(saleId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.PENDING, pending.getStatus());
        assertNotNull(pending.getNextRetryAt());

        doReturn(InvoiceResult.rejected("NIT inválido")).when(provider).emitInvoice(any());
        TenantContext.setStoreId(storeId);
        invoiceService.retryInvoice(saleId);
        ElectronicInvoice rejected = repository.findBySaleId(saleId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.REJECTED, rejected.getStatus());
        assertNull(rejected.getNextRetryAt(), "un rechazo requiere corregir datos: no se reintenta solo");
    }

    @Test
    void aPendingInvoiceWithAProviderIdIsReconciledInsteadOfEmittedAgain() {
        doReturn(InvoiceResult.pending("En espera de respuesta de la DIAN").withProviderDocumentId("doc-1"))
                .when(provider).emitInvoice(any());
        invoiceService.processElectronicInvoice(sale());
        ElectronicInvoice waiting = repository.findBySaleId(saleId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.PENDING, waiting.getStatus());
        assertEquals("doc-1", waiting.getProviderDocumentId());

        doReturn(InvoiceResult.success("SETP990000101", "cufe", "qr", null).withProviderDocumentId("doc-1"))
                .when(provider).queryInvoiceByProviderId("doc-1");
        ElectronicInvoiceDto result = invoiceService.processElectronicInvoice(sale());

        assertEquals("VALIDATED", result.status());
        verify(provider, times(1)).emitInvoice(any());
        verify(provider).queryInvoiceByProviderId("doc-1");
        ElectronicInvoice stored = repository.findBySaleId(saleId).orElseThrow();
        assertEquals("doc-1", stored.getProviderDocumentId());
        assertEquals("SETP990000101", stored.getFactusNumber());
    }

    @Test
    void ifTheReconciliationItselfFailsTheInvoiceStaysPendingAndIsNeverEmittedTwice() {
        doReturn(InvoiceResult.pending("En espera").withProviderDocumentId("doc-1")).when(provider).emitInvoice(any());
        invoiceService.processElectronicInvoice(sale());
        doThrow(new IllegalStateException("alegra caído")).when(provider).queryInvoiceByProviderId("doc-1");

        invoiceService.processElectronicInvoice(sale());

        verify(provider, times(1)).emitInvoice(any());
        assertEquals(ElectronicInvoiceStatus.PENDING, repository.findBySaleId(saleId).orElseThrow().getStatus());
    }

    @Test
    void aRejectedDocumentIsEmittedAgainOnManualRetryAndTheNewIdReplacesTheOldOne() {
        doReturn(InvoiceResult.rejected("NIT inválido").withProviderDocumentId("doc-1")).when(provider).emitInvoice(any());
        invoiceService.processElectronicInvoice(sale());
        assertEquals(ElectronicInvoiceStatus.REJECTED, repository.findBySaleId(saleId).orElseThrow().getStatus());

        doReturn(InvoiceResult.success("SETP990000102", "cufe2", "qr", null).withProviderDocumentId("doc-2"))
                .when(provider).emitInvoice(any());
        TenantContext.setStoreId(storeId);
        invoiceService.retryInvoice(saleId);

        ElectronicInvoice stored = repository.findBySaleId(saleId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored.getStatus());
        assertEquals("doc-2", stored.getProviderDocumentId());
        verify(provider, never()).queryInvoiceByProviderId(any());
    }

    @Test
    void aWaitingDocumentThatAlegraLaterReportsAsFailedIsNotStuckAndGetsEmittedAgain() {
        doReturn(InvoiceResult.pending("En espera").withProviderDocumentId("doc-1")).when(provider).emitInvoice(any());
        invoiceService.processElectronicInvoice(sale());
        doReturn(InvoiceResult.error("Alegra no pudo procesar la factura").withProviderDocumentId("doc-1"))
                .when(provider).queryInvoiceByProviderId("doc-1");

        invoiceService.processElectronicInvoice(sale());

        ElectronicInvoice failed = repository.findBySaleId(saleId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.ERROR, failed.getStatus(), "un fallo confirmado no se vuelve PENDING");
        assertFalse(failed.awaitsProviderResponse());

        doReturn(InvoiceResult.success("SETP990000103", "cufe3", "qr", null).withProviderDocumentId("doc-3"))
                .when(provider).emitInvoice(any());
        invoiceService.processElectronicInvoice(sale());

        ElectronicInvoice stored = repository.findBySaleId(saleId).orElseThrow();
        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored.getStatus());
        assertEquals("doc-3", stored.getProviderDocumentId());
        verify(provider, times(2)).emitInvoice(any());
    }
}
