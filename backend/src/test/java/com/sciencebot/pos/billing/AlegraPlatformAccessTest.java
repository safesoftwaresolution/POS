package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccess;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccessResolver;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** El acceso de plataforma usa solo el token y la URL del entorno: no necesita local ni company.id. */
class AlegraPlatformAccessTest {

    private static final String SANDBOX_URL = "https://sandbox.example/e-provider/col/v1";
    private static final String PRODUCTION_URL = "https://prod.example/e-provider/col/v1";

    private final StoreBillingConfigRepository repository = mock(StoreBillingConfigRepository.class);

    @Test
    void platformAccessUsesTheEnvironmentTokenAndUrlWithoutStoreOrCompany() {
        AlegraAccessResolver resolver = new AlegraAccessResolver(repository, SANDBOX_URL, PRODUCTION_URL + "/",
                "sandbox-token", " production-token ");

        AlegraAccess production = resolver.platformAccess(BillingEnvironment.PRODUCTION);
        AlegraAccess sandbox = resolver.platformAccess(BillingEnvironment.SANDBOX);

        assertEquals(PRODUCTION_URL, production.baseUrl());
        assertEquals("production-token", production.token());
        assertEquals(BillingEnvironment.PRODUCTION, production.environment());
        assertNull(production.storeId());
        assertNull(production.companyId());
        assertEquals(SANDBOX_URL, sandbox.baseUrl());
        assertEquals("sandbox-token", sandbox.token());
        verifyNoInteractions(repository);
    }

    @Test
    void withoutTheTokenThePlatformIsNotReady() {
        AlegraAccessResolver resolver = new AlegraAccessResolver(repository, SANDBOX_URL, PRODUCTION_URL,
                "sandbox-token", "  ");

        assertTrue(resolver.hasPlatformToken(BillingEnvironment.SANDBOX));
        assertFalse(resolver.hasPlatformToken(BillingEnvironment.PRODUCTION));
        BillingNotEnabledException ex = assertThrows(BillingNotEnabledException.class,
                () -> resolver.platformAccess(BillingEnvironment.PRODUCTION));
        assertEquals("La facturación electrónica aún no está disponible en la plataforma "
                + "(falta el token de Alegra del entorno de producción)", ex.getMessage());
    }

    @Test
    void theEnvironmentIsRequired() {
        AlegraAccessResolver resolver = new AlegraAccessResolver(repository, SANDBOX_URL, PRODUCTION_URL, "a", "b");

        assertThrows(NullPointerException.class, () -> resolver.platformAccess(null));
        assertFalse(resolver.hasPlatformToken(null));
    }
}
