package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyRegistration;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.BillingCompanyRegistrationRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.BillingCompanyRegistrationTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/** Real independent transactions verify the cross-request registration reservation, without provider calls. */
@SpringBootTest
class BillingCompanyRegistrationTest {

    private static final BillingEnvironment PRODUCTION = BillingEnvironment.PRODUCTION;

    @Autowired private BillingCompanyRegistrationTransactions transactions;
    @Autowired private BillingCompanyRegistrationRepository registrations;
    @Autowired private StoreBillingConfigRepository configs;

    private Long storeId;
    private Long otherStoreId;

    @BeforeEach
    void setUp() {
        storeId = 3_000_000L + Math.floorMod(System.nanoTime(), 1_000_000L) * 2;
        otherStoreId = storeId + 1;
        saveConfig(storeId);
        saveConfig(otherStoreId);
    }

    @AfterEach
    void cleanUp() {
        registrations.deleteAll(registrations.findAll().stream()
                .filter(r -> r.getStoreId().equals(storeId) || r.getStoreId().equals(otherStoreId)).toList());
        configs.deleteAllById(List.of(storeId, otherStoreId));
    }

    private void saveConfig(Long id) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(id);
        config.setEnvironment(PRODUCTION);
        configs.save(config);
    }

    private BillingCompanyRegistration stored(Long id) {
        return registrations.findByStoreIdAndEnvironmentAndProviderName(id, PRODUCTION, "alegra").orElseThrow();
    }

    @Test
    void concurrentSubmitsReserveOnlyOneExternalCreate() throws Exception {
        int count = 12;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(count)) {
            for (int i = 0; i < count; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        transactions.reserve(storeId, PRODUCTION, "alegra", "901234567");
                        return true;
                    } catch (BillingNotEnabledException expected) {
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) accepted++;
            }
            assertEquals(1, accepted);
        }
        assertEquals(BillingCompanyRegistration.State.CREATING, stored(storeId).getState());
        assertEquals(1, registrations.findAll().stream().filter(r -> r.getStoreId().equals(storeId)).count());
    }

    @Test
    void durableReceiptCanBeAttachedAgainAfterLocalAssociationFailure() {
        var first = transactions.reserve(storeId, PRODUCTION, "alegra", "901234567");
        transactions.recordReceipt(first, "co-" + storeId);

        var recovered = transactions.reserve(storeId, PRODUCTION, "alegra", "901234567");

        assertTrue(recovered.hasReceipt());
        assertEquals(first.operationId(), recovered.operationId());
        assertEquals("co-" + storeId, recovered.providerCompanyId());
        transactions.markAttached(recovered);
        assertEquals(BillingCompanyRegistration.State.ATTACHED, stored(storeId).getState());
    }

    @Test
    void timeoutCannotReleaseTheReservationForAnotherBlindCreate() {
        var first = transactions.reserve(storeId, PRODUCTION, "alegra", "901234567");
        transactions.recordFailure(first, false);

        assertThrows(BillingNotEnabledException.class,
                () -> transactions.reserve(storeId, PRODUCTION, "alegra", "901234567"));

        assertEquals(first.operationId(), stored(storeId).getOperationId());
        assertEquals(BillingCompanyRegistration.State.UNCERTAIN, stored(storeId).getState());
        assertNull(stored(storeId).getProviderCompanyId());
    }

    @Test
    void confirmedValidationRejectionAllowsCorrectionButRefusesAStaleReceipt() {
        var first = transactions.reserve(storeId, PRODUCTION, "alegra", "901234567");
        transactions.recordFailure(first, true);
        var corrected = transactions.reserve(storeId, PRODUCTION, "alegra", "901234568");

        assertNotEquals(first.operationId(), corrected.operationId());
        assertThrows(BillingNotEnabledException.class, () -> transactions.recordReceipt(first, "stale-company"));
        assertEquals("901234568", stored(storeId).getIdentification());
        assertEquals(corrected.operationId(), stored(storeId).getOperationId());
        assertNull(stored(storeId).getProviderCompanyId());
    }

    @Test
    void anotherStoresProviderReceiptCannotBeAdopted() {
        var first = transactions.reserve(storeId, PRODUCTION, "alegra", "901234567");
        transactions.recordReceipt(first, "co-" + storeId);
        var other = transactions.reserve(otherStoreId, PRODUCTION, "alegra", "901234568");

        assertThrows(BillingNotEnabledException.class, () -> transactions.recordReceipt(other, "co-" + storeId));
        assertNull(stored(otherStoreId).getProviderCompanyId());
        assertEquals(BillingCompanyRegistration.State.CREATING, stored(otherStoreId).getState());
    }

    @Test
    void legacyCompanyAlreadyAssignedToAnotherStoreCannotBeAdopted() {
        StoreBillingConfig legacy = configs.findById(otherStoreId).orElseThrow();
        legacy.assignCompanyId(PRODUCTION, "co-legacy-" + storeId);
        configs.save(legacy);
        var reservation = transactions.reserve(storeId, PRODUCTION, "alegra", "901234567");

        assertThrows(BillingNotEnabledException.class,
                () -> transactions.recordReceipt(reservation, "co-legacy-" + storeId));
        assertNull(stored(storeId).getProviderCompanyId());
    }

    @Test
    void environmentAndProviderHaveSeparateReservations() {
        var production = transactions.reserve(storeId, PRODUCTION, "alegra", "901234567");
        var sandbox = transactions.reserve(storeId, BillingEnvironment.SANDBOX, "alegra", "901234567");
        var mock = transactions.reserve(storeId, PRODUCTION, "mock", "901234567");

        assertNotEquals(production.operationId(), sandbox.operationId());
        assertNotEquals(production.operationId(), mock.operationId());
    }
}
