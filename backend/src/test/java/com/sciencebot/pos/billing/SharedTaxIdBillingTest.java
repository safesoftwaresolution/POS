package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.ElectronicInvoiceMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.BillingSourceResolver;
import com.sciencebot.pos.billing.internal.services.InvoiceEmissionTransactions;
import com.sciencebot.pos.businesses.BusinessFacade;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Branches of a business with a shared tax id invoice with the main store's company and numbering; the document
 * remembers that, so a later change of the business never moves a historical document to another company.
 */
@SpringBootTest
@Transactional
class SharedTaxIdBillingTest {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Autowired
    private ElectronicInvoiceRepository invoices;
    @Autowired
    private StoreBillingConfigRepository configs;
    @Autowired
    private EntityManager em;

    @SuppressWarnings("unchecked")
    private static BillingSourceResolver sourcesWhere(long storeId, long sourceId) {
        ObjectProvider<BusinessFacade> provider = mock(ObjectProvider.class);
        BusinessFacade facade = mock(BusinessFacade.class);
        when(facade.billingSourceStoreId(any())).thenAnswer(inv -> {
            Long asked = inv.getArgument(0);
            return asked == storeId ? sourceId : asked;
        });
        when(provider.getIfAvailable()).thenReturn(facade);
        return new BillingSourceResolver(provider);
    }

    private static StoreBillingConfig config(long storeId, BillingActivationStatus status, String sandboxCompany) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(storeId);
        config.setStatus(status);
        config.setEnvironment(BillingEnvironment.SANDBOX);
        if (sandboxCompany != null) config.assignCompanyId(BillingEnvironment.SANDBOX, sandboxCompany);
        return config;
    }

    @Test
    void reserve_ForABranch_UsesTheMainStoresCompanyAndPinsIt_EvenIfTheBusinessChangesLater() {
        ElectronicInvoiceRepository mocked = mock(ElectronicInvoiceRepository.class);
        StoreBillingConfigRepository mockedConfigs = mock(StoreBillingConfigRepository.class);
        ElectronicInvoice invoice = ElectronicInvoice.initialize(10L, 7L);
        when(mocked.findBySaleIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(mockedConfigs.findById(1L)).thenReturn(Optional.of(config(1L, BillingActivationStatus.ACTIVE, "main-company")));
        when(mockedConfigs.findById(7L)).thenReturn(Optional.empty());

        var first = new InvoiceEmissionTransactions(mocked, new ElectronicInvoiceMapper(), mockedConfigs,
                sourcesWhere(7L, 1L), LEASE).reserve(10L, 7L);

        assertEquals(new BillingDocumentContext(7L, BillingEnvironment.SANDBOX, "alegra", "main-company", 1L), first.context());
        assertEquals(1L, invoice.effectiveBillingStoreId());

        // The business is dissolved or switched to separate tax ids: the pinned document keeps its source.
        invoice.completeAttempt(null);
        var retried = new InvoiceEmissionTransactions(mocked, new ElectronicInvoiceMapper(), mockedConfigs,
                sourcesWhere(7L, 7L), LEASE).reserve(10L, 7L);
        assertEquals(first.context(), retried.context());
    }

    @Test
    void reserve_ForAStoreWithoutABusiness_BillsWithItsOwnCompanyAsBefore() {
        ElectronicInvoiceRepository mocked = mock(ElectronicInvoiceRepository.class);
        StoreBillingConfigRepository mockedConfigs = mock(StoreBillingConfigRepository.class);
        ElectronicInvoice invoice = ElectronicInvoice.initialize(10L, 5L);
        when(mocked.findBySaleIdForUpdate(10L)).thenReturn(Optional.of(invoice));
        when(mockedConfigs.findById(5L)).thenReturn(Optional.of(config(5L, BillingActivationStatus.ACTIVE, "own-company")));

        var reservation = new InvoiceEmissionTransactions(mocked, new ElectronicInvoiceMapper(), mockedConfigs,
                sourcesWhere(99L, 1L), LEASE).reserve(10L, 5L);

        assertEquals(new BillingDocumentContext(5L, BillingEnvironment.SANDBOX, "alegra", "own-company"), reservation.context());
        assertNull(invoice.getBillingStoreId(), "an own-store document stores no source");
    }

    @Test
    void aFiscalContextCannotMoveADocumentToAnotherSourceStore() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(10L, 7L);
        invoice.assignDocumentContext(new BillingDocumentContext(7L, BillingEnvironment.SANDBOX, "alegra", "c", 1L));

        assertThrows(IllegalStateException.class, () -> invoice.assignDocumentContext(
                new BillingDocumentContext(7L, BillingEnvironment.SANDBOX, "alegra", "c", 2L)));
        assertThrows(IllegalStateException.class, () -> invoice.assignDocumentContext(
                new BillingDocumentContext(7L, BillingEnvironment.SANDBOX, "alegra", "c", 7L)));
        invoice.assignDocumentContext(new BillingDocumentContext(7L, BillingEnvironment.SANDBOX, "alegra", "c", 1L));
    }

    @Test
    void pinBillingStore_isIgnoredOnceTheInvoiceHasItsFiscalContext() {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(10L, 7L);
        invoice.assignDocumentContext(new BillingDocumentContext(7L, BillingEnvironment.SANDBOX, "alegra", "c"));

        invoice.pinBillingStore(1L);

        assertEquals(7L, invoice.effectiveBillingStoreId());
    }

    @Test
    void retryQuery_PicksABranchInvoiceThroughItsSourceStoresActiveBilling() {
        invoicesAndConfigs();
        ElectronicInvoice branchInvoice = persistedInvoice(61_001L, 61_007L, 61_000L);
        ElectronicInvoice orphan = persistedInvoice(61_002L, 61_008L, null); // no config of its own, no source

        var due = invoices.findDueForRetry(LocalDateTime.now().plusMinutes(1), LocalDateTime.now().minusHours(1), 10,
                PageRequest.of(0, 50));

        assertTrue(due.stream().anyMatch(i -> i.getId().equals(branchInvoice.getId())));
        assertFalse(due.stream().anyMatch(i -> i.getId().equals(orphan.getId())),
                "a store without its own or a source billing config is never retried");
    }

    @Test
    void retryQuery_SkipsABranchInvoiceWhoseSourceBillingIsNotActive() {
        configs.save(config(62_000L, BillingActivationStatus.SUSPENDED, "main"));
        ElectronicInvoice branchInvoice = persistedInvoice(62_001L, 62_007L, 62_000L);

        var due = invoices.findDueForRetry(LocalDateTime.now().plusMinutes(1), LocalDateTime.now().minusHours(1), 10,
                PageRequest.of(0, 50));

        assertFalse(due.stream().anyMatch(i -> i.getId().equals(branchInvoice.getId())));
    }

    private void invoicesAndConfigs() {
        configs.save(config(61_000L, BillingActivationStatus.ACTIVE, "main"));
    }

    private ElectronicInvoice persistedInvoice(long saleId, long storeId, Long sourceStoreId) {
        ElectronicInvoice invoice = ElectronicInvoice.initialize(saleId, storeId);
        invoice.pinBillingStore(sourceStoreId);
        invoice.completeAttempt(LocalDateTime.now().minusMinutes(1));
        ElectronicInvoice saved = invoices.saveAndFlush(invoice);
        em.clear();
        return saved;
    }
}
