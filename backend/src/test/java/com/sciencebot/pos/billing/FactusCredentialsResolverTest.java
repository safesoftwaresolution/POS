package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusAccess;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusCredentialsResolver;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FactusCredentialsResolverTest {

    private static final String SANDBOX_URL = "https://sandbox.example";
    private static final String PRODUCTION_URL = "https://prod.example";

    private StoreBillingConfigRepository repository;
    private FactusCredentialsResolver resolver;

    @BeforeEach
    void setUp() {
        repository = mock(StoreBillingConfigRepository.class);
        resolver = new FactusCredentialsResolver(repository, SANDBOX_URL + "/", PRODUCTION_URL);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private StoreBillingConfig config(Long storeId, BillingActivationStatus status) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(storeId);
        config.setStatus(status);
        config.getSandbox().setClientId("sb-client");
        config.getSandbox().setClientSecret("sb-secret");
        config.getSandbox().setUsername("sb-user");
        config.getSandbox().setPassword("sb-pass");
        config.getSandbox().setNumberingRangeId(11);
        config.getProduction().setClientId("pr-client");
        config.getProduction().setClientSecret("pr-secret");
        config.getProduction().setUsername("pr-user");
        config.getProduction().setPassword("pr-pass");
        config.getProduction().setNumberingRangeId(22);
        when(repository.findById(storeId)).thenReturn(Optional.of(config));
        return config;
    }

    @Test
    void activeStoreUsesItsSandboxCredentialsAndUrl() {
        config(5L, BillingActivationStatus.ACTIVE);

        FactusAccess access = resolver.resolve(5L);

        assertEquals(BillingEnvironment.SANDBOX, access.environment());
        assertEquals(SANDBOX_URL, access.baseUrl(), "se quita la barra final");
        assertEquals("sb-client", access.clientId());
        assertEquals("sb-secret", access.clientSecret());
        assertEquals("sb-user", access.username());
        assertEquals("sb-pass", access.password());
        assertEquals(11, access.numberingRangeId());
    }

    @Test
    void productionEnvironmentUsesProductionCredentialsUrlAndRange() {
        config(5L, BillingActivationStatus.ACTIVE).setEnvironment(BillingEnvironment.PRODUCTION);

        FactusAccess access = resolver.resolve(5L);

        assertEquals(PRODUCTION_URL, access.baseUrl());
        assertEquals("pr-client", access.clientId());
        assertEquals("pr-pass", access.password());
        assertEquals(22, access.numberingRangeId());
    }

    @Test
    void eachStoreGetsOnlyItsOwnCredentials() {
        config(5L, BillingActivationStatus.ACTIVE);
        StoreBillingConfig other = config(6L, BillingActivationStatus.ACTIVE);
        other.getSandbox().setClientId("other-client");

        assertEquals("sb-client", resolver.resolve(5L).clientId());
        assertEquals("other-client", resolver.resolve(6L).clientId());
    }

    @Test
    void storeThatIsNotActiveCannotBill() {
        for (BillingActivationStatus status : BillingActivationStatus.values()) {
            if (status == BillingActivationStatus.ACTIVE) {
                continue;
            }
            config(5L, status);
            BillingNotEnabledException ex = assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(5L));
            assertTrue(ex.getMessage().contains(status.name()), status.name());
        }
    }

    @Test
    void storeWithoutConfigCannotBillAndThereIsNoGlobalFallback() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(9L));
    }

    @Test
    void activeStoreWithIncompleteCredentialsForItsEnvironmentCannotBill() {
        StoreBillingConfig config = config(5L, BillingActivationStatus.ACTIVE);
        config.getSandbox().setPassword(null);

        BillingNotEnabledException ex = assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(5L));
        assertTrue(ex.getMessage().contains("SANDBOX"));
    }

    @Test
    void nullStoreIdUsesTheStoreOfTheCurrentRequest() {
        config(5L, BillingActivationStatus.ACTIVE);
        TenantContext.setStoreId(5L);

        assertEquals(5L, resolver.resolve(null).storeId());
    }

    @Test
    void nullStoreIdWithoutRequestContextFails() {
        assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(null));
    }

    @Test
    void resolveIgnoringStatusLetsTheSuperAdminTestCredentialsBeforeActivation() {
        config(5L, BillingActivationStatus.AWAITING_PROVIDER);

        assertEquals("sb-client", resolver.resolveIgnoringStatus(5L).clientId());
    }

    @Test
    void resolveIgnoringStatusStillNeedsCompleteCredentials() {
        config(5L, BillingActivationStatus.AWAITING_PROVIDER).getSandbox().setClientSecret("");

        assertThrows(BillingNotEnabledException.class, () -> resolver.resolveIgnoringStatus(5L));
    }

    @Test
    void resolveIgnoringStatusWithExplicitEnvironmentTestsAnEnvironmentThatIsNotTheActiveOne() {
        config(5L, BillingActivationStatus.ACTIVE); // el local está activo en SANDBOX

        FactusAccess prodAccess = resolver.resolveIgnoringStatus(5L, BillingEnvironment.PRODUCTION);

        assertEquals(BillingEnvironment.PRODUCTION, prodAccess.environment());
        assertEquals(PRODUCTION_URL, prodAccess.baseUrl());
        assertEquals("pr-client", prodAccess.clientId());
    }

    @Test
    void resolveIgnoringStatusWithExplicitEnvironmentStillNeedsCompleteCredentials() {
        config(5L, BillingActivationStatus.ACTIVE).getProduction().setUsername(null);

        assertThrows(BillingNotEnabledException.class,
                () -> resolver.resolveIgnoringStatus(5L, BillingEnvironment.PRODUCTION));
    }

    @Test
    void accessNeverPrintsSecrets() {
        config(5L, BillingActivationStatus.ACTIVE);

        String printed = resolver.resolve(5L).toString();

        assertFalse(printed.contains("sb-secret"));
        assertFalse(printed.contains("sb-pass"));
    }
}
