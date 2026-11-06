package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.BillingActivationMapper;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigServiceImpl;
import com.sciencebot.pos.notifications.NotificationFacade;
import com.sciencebot.pos.billing.internal.adapters.BillingOnboardingProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** El company.id del proveedor (Alegra) se guarda por local y entorno, solo desde el back office. */
class StoreBillingConfigCompanyIdServiceTest {

    private static final Long STORE = 7L;

    private final Map<Long, StoreBillingConfig> db = new HashMap<>();
    private ElectronicInvoicingProvider provider;
    private StoreBillingConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        StoreBillingConfigRepository repository = mock(StoreBillingConfigRepository.class);
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<Long>getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> {
            StoreBillingConfig saved = inv.getArgument(0);
            db.put(saved.getStoreId(), saved);
            return saved;
        });
        provider = mock(ElectronicInvoicingProvider.class);
        when(provider.getProviderName()).thenReturn("alegra");
        when(provider.displayName()).thenReturn("Alegra");
        ActiveBillingProvider active = new ActiveBillingProvider(List.of(provider), "alegra");
        service = new StoreBillingConfigServiceImpl(repository, new BillingActivationMapper(active), active,
                mock(NotificationFacade.class), mock(BillingOnboardingProvider.class),
                mock(PlatformTransactionManager.class));
    }

    @Test
    void updateProviderCompanyId_StoresItPerEnvironmentTrimmedAndAuditsTheActor() {
        BillingActivationDto dto = service.updateProviderCompanyId(
                STORE, BillingEnvironment.SANDBOX, "  01M6KH7R1QZQK687BNMX5BBXZC ", "super@platform.co");

        StoreBillingConfig saved = db.get(STORE);
        assertEquals("01M6KH7R1QZQK687BNMX5BBXZC", saved.companyIdFor(BillingEnvironment.SANDBOX));
        assertNull(saved.companyIdFor(BillingEnvironment.PRODUCTION));
        assertEquals("super@platform.co", saved.getUpdatedBy());
        assertTrue(dto.sandboxCompanyIdConfigured());
        assertFalse(dto.productionCompanyIdConfigured());
    }

    @Test
    void updateProviderCompanyId_IsIdempotent() {
        service.updateProviderCompanyId(STORE, BillingEnvironment.PRODUCTION, "abc", "a@x.co");
        BillingActivationDto again = service.updateProviderCompanyId(STORE, BillingEnvironment.PRODUCTION, "abc", "a@x.co");

        assertTrue(again.productionCompanyIdConfigured());
        assertEquals("abc", db.get(STORE).companyIdFor(BillingEnvironment.PRODUCTION));
    }

    @Test
    void updateProviderCompanyId_InvalidatesProviderCacheForTheStore() {
        service.updateProviderCompanyId(STORE, BillingEnvironment.SANDBOX, "abc", "a@x.co");
        verify(provider).onAccountChanged(STORE);
    }

    @Test
    void updateProviderCompanyId_RejectsBlankWithoutSavingAnything() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateProviderCompanyId(STORE, BillingEnvironment.SANDBOX, "  ", "a@x.co"));
        assertTrue(db.isEmpty());
    }

    @Test
    void get_ReportsNoCompanyIdForAStoreWithoutConfig() {
        BillingActivationDto dto = service.get(STORE);
        assertFalse(dto.sandboxCompanyIdConfigured());
        assertFalse(dto.productionCompanyIdConfigured());
    }
}
