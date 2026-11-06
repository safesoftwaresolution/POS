package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccess;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccessResolver;
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

/** Con Alegra el aislamiento es por company.id: cada local usa solo el suyo y el token de plataforma del entorno. */
class AlegraAccessResolverTest {

    private static final String SANDBOX_URL = "https://sandbox.example/e-provider/col/v1";
    private static final String PRODUCTION_URL = "https://prod.example/e-provider/col/v1";

    private StoreBillingConfigRepository repository;
    private AlegraAccessResolver resolver;

    @BeforeEach
    void setUp() {
        repository = mock(StoreBillingConfigRepository.class);
        resolver = new AlegraAccessResolver(repository, SANDBOX_URL + "/", PRODUCTION_URL, "sandbox-token", "production-token");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private StoreBillingConfig config(Long storeId, BillingActivationStatus status, String sandboxCompany, String productionCompany) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(storeId);
        config.setStatus(status);
        if (sandboxCompany != null) config.assignCompanyId(BillingEnvironment.SANDBOX, sandboxCompany);
        if (productionCompany != null) config.assignCompanyId(BillingEnvironment.PRODUCTION, productionCompany);
        when(repository.findById(storeId)).thenReturn(Optional.of(config));
        return config;
    }

    @Test
    void activeStoreUsesItsCompanyIdTheEnvironmentTokenAndUrl() {
        config(5L, BillingActivationStatus.ACTIVE, "company-sb", "company-pr");

        AlegraAccess access = resolver.resolve(5L);

        assertEquals(BillingEnvironment.SANDBOX, access.environment());
        assertEquals(SANDBOX_URL, access.baseUrl(), "se quita la barra final");
        assertEquals("sandbox-token", access.token());
        assertEquals("company-sb", access.companyId());
        assertEquals(5L, access.storeId());
    }

    @Test
    void productionEnvironmentUsesTheProductionTokenUrlAndCompany() {
        StoreBillingConfig config = config(5L, BillingActivationStatus.ACTIVE, "company-sb", "company-pr");
        config.setEnvironment(BillingEnvironment.PRODUCTION);

        AlegraAccess access = resolver.resolve(5L);

        assertEquals(PRODUCTION_URL, access.baseUrl());
        assertEquals("production-token", access.token());
        assertEquals("company-pr", access.companyId());
    }

    @Test
    void withoutAnExplicitStoreTheRequestsStoreIsUsed() {
        config(7L, BillingActivationStatus.ACTIVE, "company-7", null);
        TenantContext.setStoreId(7L);

        assertEquals("company-7", resolver.resolve(null).companyId());
    }

    @Test
    void noStoreAtAll_FailsWithoutFallingBackToAnotherStore() {
        assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(null));
    }

    @Test
    void aStoreNeverUsesAnotherStoresCompanyId() {
        config(5L, BillingActivationStatus.ACTIVE, "company-5", null);
        config(6L, BillingActivationStatus.ACTIVE, "company-6", null);

        assertEquals("company-5", resolver.resolve(5L).companyId());
        assertEquals("company-6", resolver.resolve(6L).companyId());
    }

    @Test
    void aStoreWithoutBillingActiveCannotResolveButCanBeTestedIgnoringStatus() {
        config(5L, BillingActivationStatus.IN_SETUP, "company-5", null);

        assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(5L));
        assertEquals("company-5", resolver.resolveIgnoringStatus(5L, BillingEnvironment.SANDBOX).companyId());
    }

    @Test
    void missingCompanyIdOrTokenFailsAsNotEnabled() {
        config(5L, BillingActivationStatus.ACTIVE, null, null);
        assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(5L));

        AlegraAccessResolver withoutToken = new AlegraAccessResolver(repository, SANDBOX_URL, PRODUCTION_URL, "", "");
        config(6L, BillingActivationStatus.ACTIVE, "company-6", null);
        assertThrows(BillingNotEnabledException.class, () -> withoutToken.resolve(6L));
    }

    @Test
    void theMessageSaysWhetherTheServerTokenOrTheStoreCompanyIsMissing() {
        config(5L, BillingActivationStatus.ACTIVE, null, null);
        String noCompany = assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(5L)).getMessage();
        assertTrue(noCompany.contains("empresa"), noCompany);
        assertTrue(noCompany.contains("pruebas"), noCompany);
        assertFalse(noCompany.contains("token"), noCompany);

        AlegraAccessResolver withoutToken = new AlegraAccessResolver(repository, SANDBOX_URL, PRODUCTION_URL, "", "");
        config(6L, BillingActivationStatus.ACTIVE, "company-6", null);
        String noToken = assertThrows(BillingNotEnabledException.class, () -> withoutToken.resolve(6L)).getMessage();
        assertTrue(noToken.contains("token"), noToken);
        assertTrue(noToken.contains("servidor"), noToken);

        for (String message : new String[]{noCompany, noToken}) {
            assertFalse(message.contains("credenciales"), message);
            assertFalse(message.contains("SANDBOX"), message);
        }
    }

    @Test
    void anInactiveStoreIsDescribedWithASpanishLabelNotTheEnumName() {
        config(5L, BillingActivationStatus.NOT_REQUESTED, "company-5", null);

        String message = assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(5L)).getMessage();

        assertFalse(message.contains("NOT_REQUESTED"), message);
        assertTrue(message.contains("no solicitada"), message);
    }

    @Test
    void platformAccessWithoutTokenNamesTheEnvironmentInSpanish() {
        AlegraAccessResolver withoutToken = new AlegraAccessResolver(repository, SANDBOX_URL, PRODUCTION_URL, "", "");

        String message = assertThrows(BillingNotEnabledException.class,
                () -> withoutToken.platformAccess(BillingEnvironment.PRODUCTION)).getMessage();

        assertTrue(message.contains("producción"), message);
        assertFalse(message.contains("PRODUCTION"), message);
    }

    @Test
    void storeWithoutBillingRowFailsAsNotEnabled() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(BillingNotEnabledException.class, () -> resolver.resolve(9L));
    }

    @Test
    void isConfigured_NeedsTheEnvironmentTokenAndTheStoresCompanyId() {
        config(5L, BillingActivationStatus.NOT_REQUESTED, "company-sb", null);

        assertTrue(resolver.isConfigured(5L, BillingEnvironment.SANDBOX), "no exige que este ACTIVE");
        assertFalse(resolver.isConfigured(5L, BillingEnvironment.PRODUCTION), "falta el company.id de produccion");
        assertFalse(resolver.isConfigured(99L, BillingEnvironment.SANDBOX), "sin fila: false, no error");
        assertFalse(resolver.isConfigured(null, BillingEnvironment.SANDBOX));
        assertFalse(new AlegraAccessResolver(repository, SANDBOX_URL, PRODUCTION_URL, " ", "x")
                .isConfigured(5L, BillingEnvironment.SANDBOX), "sin token del entorno");
    }

    @Test
    void toStringNeverPrintsTheToken() {
        config(5L, BillingActivationStatus.ACTIVE, "company-sb", null);

        String printed = resolver.resolve(5L).toString();

        assertFalse(printed.contains("sandbox-token"), printed);
        assertTrue(printed.contains("5"));
    }
}
