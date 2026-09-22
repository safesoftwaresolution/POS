package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.BillingActivationMapper;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoreBillingConfigServiceImplTest {

    private static final Long STORE = 7L;
    private static final Long OTHER_STORE = 8L;

    private final Map<Long, StoreBillingConfig> db = new HashMap<>();
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
        service = new StoreBillingConfigServiceImpl(repository, new BillingActivationMapper());
    }

    private static UpdateBillingCredentialsCommand fullCredentials() {
        return new UpdateBillingCredentialsCommand("client-1", "secret-1", "user@x.co", "pass-1", 42);
    }

    /** Lleva al local por el camino normal hasta el estado indicado. */
    private void moveTo(BillingActivationStatus target) {
        BillingActivationStatus[] path = {
                BillingActivationStatus.DOCUMENTS_SUBMITTED,
                BillingActivationStatus.AWAITING_PROVIDER,
                BillingActivationStatus.ACTIVE
        };
        for (BillingActivationStatus step : path) {
            service.transition(STORE, step, null, "sa");
            if (step == target) {
                return;
            }
        }
    }

    @Test
    void storeWithoutRowIsNotRequested() {
        BillingActivationDto dto = service.get(STORE);

        assertEquals(BillingActivationStatus.NOT_REQUESTED, dto.status());
        assertEquals(BillingEnvironment.SANDBOX, dto.environment());
        assertFalse(dto.sandboxConfigured());
        assertTrue(db.isEmpty(), "leer no debe crear la fila");
    }

    @Test
    void invalidTransitionIsRejected() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        assertTrue(ex.getMessage().contains("NOT_REQUESTED"));
        assertTrue(db.isEmpty());
    }

    @Test
    void rejectionAndSuspensionNeedAReason() {
        service.transition(STORE, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, "admin");

        assertThrows(IllegalArgumentException.class,
                () -> service.transition(STORE, BillingActivationStatus.REJECTED, "  ", "sa"));

        BillingActivationDto dto = service.transition(STORE, BillingActivationStatus.REJECTED, "RUT ilegible", "sa");
        assertEquals("RUT ilegible", dto.statusReason());

        BillingActivationDto resubmitted =
                service.transition(STORE, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, "admin");
        assertNull(resubmitted.statusReason(), "el motivo se borra al salir de REJECTED");
    }

    @Test
    void cannotActivateWithoutCredentialsOfTheActiveEnvironment() {
        moveTo(BillingActivationStatus.AWAITING_PROVIDER);

        assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        // Credenciales de producción no sirven mientras el entorno activo es SANDBOX.
        service.updateCredentials(STORE, BillingEnvironment.PRODUCTION, fullCredentials(), "sa");
        assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        service.updateCredentials(STORE, BillingEnvironment.SANDBOX, fullCredentials(), "sa");
        BillingActivationDto dto = service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa");
        assertEquals(BillingActivationStatus.ACTIVE, dto.status());
    }

    @Test
    void blankSecretKeepsTheStoredOne() {
        service.updateCredentials(STORE, BillingEnvironment.SANDBOX, fullCredentials(), "sa");

        service.updateCredentials(STORE, BillingEnvironment.SANDBOX,
                new UpdateBillingCredentialsCommand("client-2", "", "user2@x.co", null, null), "sa");

        var credentials = db.get(STORE).getSandbox();
        assertEquals("client-2", credentials.getClientId());
        assertEquals("user2@x.co", credentials.getUsername());
        assertEquals("secret-1", credentials.getClientSecret());
        assertEquals("pass-1", credentials.getPassword());
        assertEquals(42, credentials.getNumberingRangeId());
    }

    @Test
    void blankClientIdOrUsernameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.updateCredentials(STORE,
                BillingEnvironment.SANDBOX, new UpdateBillingCredentialsCommand(" ", null, null, null, null), "sa"));
        assertThrows(IllegalArgumentException.class, () -> service.updateCredentials(STORE,
                BillingEnvironment.SANDBOX, new UpdateBillingCredentialsCommand(null, null, "", null, null), "sa"));
    }

    @Test
    void dtoNeverExposesSecretsOnlyWhetherEnvironmentIsComplete() {
        BillingActivationDto dto =
                service.updateCredentials(STORE, BillingEnvironment.PRODUCTION, fullCredentials(), "sa");

        assertTrue(dto.productionConfigured());
        assertFalse(dto.sandboxConfigured());
        assertEquals(42, dto.productionNumberingRangeId());
        assertFalse(dto.toString().contains("secret-1"));
        assertFalse(dto.toString().contains("pass-1"));
    }

    @Test
    void credentialsAreKeptPerStore() {
        service.updateCredentials(STORE, BillingEnvironment.SANDBOX, fullCredentials(), "sa");

        assertFalse(service.get(OTHER_STORE).sandboxConfigured());
        assertTrue(service.get(STORE).sandboxConfigured());
    }

    @Test
    void activeStoreCannotSwitchToAnEnvironmentWithoutCredentials() {
        service.updateCredentials(STORE, BillingEnvironment.SANDBOX, fullCredentials(), "sa");
        moveTo(BillingActivationStatus.ACTIVE);

        assertThrows(IllegalStateException.class,
                () -> service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "sa"));

        service.updateCredentials(STORE, BillingEnvironment.PRODUCTION, fullCredentials(), "sa");
        assertEquals(BillingEnvironment.PRODUCTION,
                service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "sa").environment());
    }

    @Test
    void recordsWhoMadeTheLastChange() {
        service.transition(STORE, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, "admin@local.co");
        assertEquals("admin@local.co", db.get(STORE).getUpdatedBy());

        service.transition(STORE, BillingActivationStatus.AWAITING_PROVIDER, null, "super@platform.co");
        assertEquals("super@platform.co", db.get(STORE).getUpdatedBy());
    }
}
