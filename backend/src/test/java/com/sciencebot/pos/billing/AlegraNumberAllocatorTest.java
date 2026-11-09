package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator.Allocation;
import com.sciencebot.pos.billing.internal.entities.NumberingRange;
import com.sciencebot.pos.billing.internal.repositories.DocumentNumberAllocationRepository;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El consecutivo que Alegra exige lo asigna el sistema: sin huecos ni duplicados aunque haya hilos reales
 * emitiendo a la vez, e idempotente por referencia. Se ejecuta sin transaccion de test para que cada hilo
 * tenga la suya (igual que StockConcurrencyTest).
 */
@SpringBootTest
class AlegraNumberAllocatorTest {

    private static final BillingEnvironment ENV = BillingEnvironment.SANDBOX;
    private static final String INVOICE = "01";

    @Autowired
    private NumberingRangeRepository rangeRepository;
    @Autowired
    private DocumentNumberAllocationRepository allocationRepository;
    @Autowired
    private StoreBillingConfigRepository configRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private AlegraNumberAllocator allocator;
    private Long storeId;

    @BeforeEach
    void setUp() {
        allocator = new AlegraNumberAllocator(rangeRepository, allocationRepository, configRepository, transactionManager);
        // Un local distinto por test: los hilos de un test nunca tocan los datos de otro.
        storeId = 100_000L + (System.nanoTime() % 1_000_000L);
    }

    @AfterEach
    void cleanUp() {
        allocationRepository.deleteAll(allocationRepository.findAll().stream()
                .filter(a -> a.getStoreId().equals(storeId)).toList());
        configRepository.findById(storeId).ifPresent(configRepository::delete);
        rangeRepository.deleteAll(rangeRepository.findAll().stream()
                .filter(r -> r.getStoreId().equals(storeId)).toList());
    }

    private NumberingRange createRange(long min, long max, LocalDate start, LocalDate end) {
        return rangeRepository.save(new NumberingRange(storeId, ENV, INVOICE, "SETP", "18760000001", min, max,
                start, end, "technical-key"));
    }

    @Test
    void parallelEmissions_getUniqueConsecutiveNumbersWithoutGaps() throws Exception {
        NumberingRange range = createRange(990_000_000L, 995_000_000L, null, null);
        int threads = 24;

        List<Allocation> results = runParallel(threads, i -> "SALE-" + i);

        Set<Long> numbers = new TreeSet<>();
        results.forEach(a -> numbers.add(a.number()));
        assertEquals(threads, numbers.size(), "no puede haber numeros repetidos");
        assertEquals(990_000_000L, ((TreeSet<Long>) numbers).first());
        assertEquals(990_000_000L + threads - 1, ((TreeSet<Long>) numbers).last(), "sin huecos");
        assertEquals(990_000_000L + threads, rangeRepository.findById(range.getId()).orElseThrow().getNextNumber());
    }

    @Test
    void sameReferenceInParallel_consumesASingleNumber() throws Exception {
        NumberingRange range = createRange(1L, 100L, null, null);

        List<Allocation> results = runParallel(10, i -> "SALE-SAME");

        assertEquals(1, results.stream().map(Allocation::number).distinct().count());
        assertEquals(2L, rangeRepository.findById(range.getId()).orElseThrow().getNextNumber());
    }

    @Test
    void retryWithTheSameReference_returnsTheSameNumberPrefixAndRange() {
        NumberingRange range = createRange(500L, 600L, null, null);

        Allocation first = allocator.allocate(storeId, ENV, INVOICE, "SALE-1");
        Allocation again = allocator.allocate(storeId, ENV, INVOICE, "SALE-1");
        Allocation other = allocator.allocate(storeId, ENV, INVOICE, "SALE-2");

        assertEquals(500L, first.number());
        assertEquals(first.number(), again.number());
        assertEquals(first.id(), again.id());
        assertEquals("SETP", first.prefix());
        assertEquals(range.getId(), first.rangeId());
        assertEquals("18760000001", first.resolutionNumber());
        assertEquals("technical-key", first.technicalKey());
        assertEquals(501L, other.number());
        assertFalse(first.completed());
        assertNotNull(first.idempotencyKey());
    }

    @Test
    void exhaustedRange_failsWithAnActionableMessageAndKeepsAssignedNumbers() {
        createRange(1L, 1L, null, null);
        Allocation first = allocator.allocate(storeId, ENV, INVOICE, "SALE-1");

        BillingNotEnabledException error = assertThrows(BillingNotEnabledException.class,
                () -> allocator.allocate(storeId, ENV, INVOICE, "SALE-2"));

        assertTrue(error.getMessage().toLowerCase().contains("agotado"), error.getMessage());
        assertEquals(first.number(), allocator.allocate(storeId, ENV, INVOICE, "SALE-1").number());
    }

    @Test
    void expiredAndNotYetValidRanges_failWithAClearMessage() {
        NumberingRange expired = createRange(1L, 10L, LocalDate.now().minusYears(2), LocalDate.now().minusDays(1));
        BillingNotEnabledException expiredError = assertThrows(BillingNotEnabledException.class,
                () -> allocator.allocate(storeId, ENV, INVOICE, "SALE-1"));
        assertTrue(expiredError.getMessage().toLowerCase().contains("venci"), expiredError.getMessage());
        rangeRepository.delete(expired);

        createRange(1L, 10L, LocalDate.now().plusDays(3), LocalDate.now().plusYears(1));
        BillingNotEnabledException futureError = assertThrows(BillingNotEnabledException.class,
                () -> allocator.allocate(storeId, ENV, INVOICE, "SALE-2"));
        assertTrue(futureError.getMessage().toLowerCase().contains("vigente"), futureError.getMessage());
    }

    @Test
    void withoutAnActiveRange_failsWithAClearMessage() {
        BillingNotEnabledException error = assertThrows(BillingNotEnabledException.class,
                () -> allocator.allocate(storeId, ENV, INVOICE, "SALE-1"));
        assertTrue(error.getMessage().toLowerCase().contains("rango"), error.getMessage());
    }

    private void chooseDefault(NumberingRange range) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(storeId);
        config.setEnvironment(ENV);
        config.assignNumberingRangeId(ENV, Math.toIntExact(range.getId()));
        configRepository.save(config);
    }

    private NumberingRange createRange(String prefix, String resolution, long min, long max, LocalDate start,
                                       LocalDate end) {
        return rangeRepository.save(new NumberingRange(storeId, ENV, INVOICE, prefix, resolution, min, max,
                start, end, "technical-key"));
    }

    @Test
    void theStoreDefaultRangeIsPreferredWhenItIsUsable() {
        createRange("OLD", "1", 1L, 100L, null, null);
        NumberingRange chosen = createRange("NEW", "2", 1000L, 2000L, null, null);
        chooseDefault(chosen);

        Allocation allocation = allocator.allocate(storeId, ENV, INVOICE, "SALE-1");

        assertEquals(chosen.getId(), allocation.rangeId());
        assertEquals("NEW", allocation.prefix());
        assertEquals(1000L, allocation.number());
    }

    @Test
    void anExpiredOrExhaustedDefaultFallsBackToTheFirstUsableRange() {
        NumberingRange first = createRange("FE", "1", 1L, 100L, null, null);
        NumberingRange expired = createRange("EXP", "2", 1L, 100L, LocalDate.now().minusYears(2),
                LocalDate.now().minusDays(1));
        chooseDefault(expired);

        assertEquals(first.getId(), allocator.allocate(storeId, ENV, INVOICE, "SALE-1").rangeId());

        NumberingRange exhausted = createRange("AGO", "3", 1L, 1L, null, null);
        exhausted.takeNextNumber();
        rangeRepository.save(exhausted);
        chooseDefault(exhausted);

        assertEquals(first.getId(), allocator.allocate(storeId, ENV, INVOICE, "SALE-2").rangeId());
    }

    @Test
    void theDefaultRangeOfAnotherDocumentDoesNotChangeTheInvoiceRange() {
        NumberingRange invoices = createRange("FE", "1", 1L, 100L, null, null);
        NumberingRange notes = rangeRepository.save(new NumberingRange(storeId, ENV, "91", "NC", "SIN-RESOLUCION",
                1L, 100L, null, null, null));
        chooseDefault(notes);

        assertEquals(invoices.getId(), allocator.allocate(storeId, ENV, INVOICE, "SALE-1").rangeId());
    }

    @Test
    void parallelEmissionsOnTheDefaultRangeStayUniqueAndWithoutGaps() throws Exception {
        createRange("OLD", "1", 1L, 100L, null, null);
        NumberingRange chosen = createRange("NEW", "2", 5000L, 6000L, null, null);
        chooseDefault(chosen);

        List<Allocation> results = runParallel(12, i -> "SALE-" + i);

        TreeSet<Long> numbers = new TreeSet<>();
        results.forEach(a -> numbers.add(a.number()));
        assertEquals(12, numbers.size());
        assertEquals(5000L, numbers.first());
        assertEquals(5011L, numbers.last());
        assertTrue(results.stream().allMatch(a -> a.rangeId().equals(chosen.getId())));
    }

    @Test
    void anotherStoreNeverUsesThisStoresRange() {
        createRange(1L, 10L, null, null);

        assertThrows(BillingNotEnabledException.class,
                () -> allocator.allocate(storeId + 1, ENV, INVOICE, "SALE-1"));
    }

    @Test
    void rotateIdempotencyKey_changesTheKeyAndKeepsTheNumber() {
        createRange(10L, 20L, null, null);
        Allocation first = allocator.allocate(storeId, ENV, INVOICE, "SALE-1");
        allocator.recordResult(first.id(), InvoiceResult.rejected("Correctable data"));

        Allocation rotated = allocator.rotateIdempotencyKey(first.id());

        assertNotEquals(first.idempotencyKey(), rotated.idempotencyKey());
        assertEquals(first.number(), rotated.number());
        assertEquals(rotated.idempotencyKey(), allocator.allocate(storeId, ENV, INVOICE, "SALE-1").idempotencyKey());
    }

    @Test
    void markCompleted_flagsTheAllocation() {
        createRange(10L, 20L, null, null);
        Allocation first = allocator.allocate(storeId, ENV, INVOICE, "SALE-1");

        allocator.markCompleted(first.id());

        assertTrue(allocator.allocate(storeId, ENV, INVOICE, "SALE-1").completed());
    }

    @Test
    void uncertainPayloadIsFrozenAndAnAcceptedReceiptSurvivesLocalCompletionFailure() {
        createRange(10L, 20L, null, null);
        Allocation first = allocator.allocate(storeId, ENV, INVOICE, "SALE-RECOVER");
        String payload = allocator.freezeRequest(first.id(), Map.of("number", first.number(), "description", "original"));
        assertEquals(payload, allocator.freezeRequest(first.id(), Map.of("description", "changed")));
        assertThrows(IllegalStateException.class, () -> allocator.rotateIdempotencyKey(first.id()));

        InvoiceResult accepted = InvoiceResult.success("SETP10", "cufe-recovered", "qr", null).withProviderDocumentId("provider-10");
        allocator.recordResult(first.id(), accepted);
        // Deliberately do not complete the invoice transaction: a fresh allocation read must retain the receipt.
        Allocation recovered = allocator.allocate(storeId, ENV, INVOICE, "SALE-RECOVER");
        assertEquals("ACCEPTED", recovered.outcome());
        assertEquals(first.idempotencyKey(), recovered.idempotencyKey());
        assertEquals(first.number(), recovered.number());
        assertEquals(payload, recovered.requestPayload());
        assertEquals("cufe-recovered", allocator.receipt(recovered).cufe());
        assertEquals("provider-10", allocator.receipt(recovered).providerDocumentId());
        assertThrows(IllegalStateException.class, () -> allocator.rotateIdempotencyKey(recovered.id()));
    }

    /** Lanza {@code threads} asignaciones a la vez; falla el test si alguna lanza excepcion. */
    private List<Allocation> runParallel(int threads, java.util.function.IntFunction<String> reference) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Allocation>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String ref = reference.apply(i);
            futures.add(pool.submit(() -> {
                start.await();
                return allocator.allocate(storeId, ENV, INVOICE, ref);
            }));
        }
        start.countDown();
        List<Allocation> results = new ArrayList<>();
        for (Future<Allocation> future : futures) {
            results.add(future.get()); // propaga cualquier excepcion de unicidad o de lock
        }
        pool.shutdown();
        return results;
    }
}
