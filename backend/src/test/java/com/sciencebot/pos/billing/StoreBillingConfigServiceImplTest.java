package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.api.*;
import com.sciencebot.pos.billing.dto.*;
import com.sciencebot.pos.billing.exception.*;
import com.sciencebot.pos.billing.internal.adapters.factus.FactusTokenManager;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.BillingActivationMapper;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigServiceImpl;
import com.sciencebot.pos.notifications.CreateNotificationCommand;
import com.sciencebot.pos.notifications.NotificationFacade;
import com.sciencebot.pos.stores.StoreDocumentDto;
import com.sciencebot.pos.stores.StoreFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StoreBillingConfigServiceImplTest {

    private static final Long STORE = 7L;
    private static final Long OTHER_STORE = 8L;

    private final Map<Long, StoreBillingConfig> db = new HashMap<>();
    private FactusTokenManager tokenManager;
    private NotificationFacade notificationFacade;
    private StoreFacade storeFacade;
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
        tokenManager = mock(FactusTokenManager.class);
        notificationFacade = mock(NotificationFacade.class);
        storeFacade = mock(StoreFacade.class);
        // Por defecto el local ya tiene documentos subidos (no es lo que prueban estos tests).
        when(storeFacade.getDocuments(any())).thenReturn(
                List.of(new StoreDocumentDto(1L, STORE, "RUT", "url", "PENDING", null, null, null)));

        service = new StoreBillingConfigServiceImpl(repository, new BillingActivationMapper(), tokenManager,
                notificationFacade, storeFacade);
    }

    private static UpdateBillingCredentialsCommand fullCredentials() {
        return new UpdateBillingCredentialsCommand("client-1", "secret-1", "user@x.co", "pass-1");
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
                new UpdateBillingCredentialsCommand("client-2", "", "user2@x.co", null), "sa");

        var credentials = db.get(STORE).getSandbox();
        assertEquals("client-2", credentials.getClientId());
        assertEquals("user2@x.co", credentials.getUsername());
        assertEquals("secret-1", credentials.getClientSecret());
        assertEquals("pass-1", credentials.getPassword());
    }

    @Test
    void blankClientIdOrUsernameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.updateCredentials(STORE,
                BillingEnvironment.SANDBOX, new UpdateBillingCredentialsCommand(" ", null, null, null), "sa"));
        assertThrows(IllegalArgumentException.class, () -> service.updateCredentials(STORE,
                BillingEnvironment.SANDBOX, new UpdateBillingCredentialsCommand(null, null, "", null), "sa"));
    }

    @Test
    void dtoNeverExposesSecretsOnlyWhetherEnvironmentIsComplete() {
        BillingActivationDto dto =
                service.updateCredentials(STORE, BillingEnvironment.PRODUCTION, fullCredentials(), "sa");

        assertTrue(dto.productionConfigured());
        assertFalse(dto.sandboxConfigured());
        assertNull(dto.productionNumberingRangeId());
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

    // --- updateCredentials invalida el token guardado ---

    @Test
    void updatingCredentialsInvalidatesTheCachedToken() {
        service.updateCredentials(STORE, BillingEnvironment.SANDBOX, fullCredentials(), "sa");

        verify(tokenManager).invalidate(STORE);
    }

    // --- requestActivation (lo origina el local) ---

    @Test
    void requestActivationMovesToDocumentsSubmittedAndDoesNotNotifyTheStoreItself() {
        BillingActivationDto dto = service.requestActivation(STORE, "admin@local.co");

        assertEquals(BillingActivationStatus.DOCUMENTS_SUBMITTED, dto.status());
        assertEquals("admin@local.co", db.get(STORE).getUpdatedBy());
        verifyNoInteractions(notificationFacade);
    }

    @Test
    void requestActivationNeedsAtLeastOneUploadedDocument() {
        when(storeFacade.getDocuments(STORE)).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.requestActivation(STORE, "admin@local.co"));

        assertTrue(ex.getMessage().contains("documento"));
        assertTrue(db.isEmpty());
    }

    @Test
    void requestActivationCannotBeCalledTwiceInARow() {
        service.requestActivation(STORE, "admin@local.co");

        assertThrows(IllegalStateException.class, () -> service.requestActivation(STORE, "admin@local.co"));
    }

    @Test
    void requestActivationCanBeRetriedAfterRejection() {
        service.requestActivation(STORE, "admin@local.co");
        service.transition(STORE, BillingActivationStatus.REJECTED, "RUT ilegible", "sa");

        BillingActivationDto dto = service.requestActivation(STORE, "admin@local.co");

        assertEquals(BillingActivationStatus.DOCUMENTS_SUBMITTED, dto.status());
    }

    // --- transition SÍ notifica al local (lo origina el super admin) ---

    @Test
    void transitionToAwaitingProviderNotifiesTheStore() {
        service.transition(STORE, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, "admin");
        reset(notificationFacade);

        service.transition(STORE, BillingActivationStatus.AWAITING_PROVIDER, null, "sa");

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationFacade).createNotification(captor.capture());
        assertEquals(STORE, captor.getValue().storeId());
        assertEquals("BILLING_STATUS_CHANGED", captor.getValue().type());
    }

    @Test
    void rejectionNotificationIncludesTheReason() {
        service.transition(STORE, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, "admin");
        reset(notificationFacade);

        service.transition(STORE, BillingActivationStatus.REJECTED, "RUT ilegible", "sa");

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationFacade).createNotification(captor.capture());
        assertTrue(captor.getValue().message().contains("RUT ilegible"));
    }

    @Test
    void activationNotifiesTheStore() {
        service.updateCredentials(STORE, BillingEnvironment.SANDBOX, fullCredentials(), "sa");
        service.transition(STORE, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, "admin");
        service.transition(STORE, BillingActivationStatus.AWAITING_PROVIDER, null, "sa");
        reset(notificationFacade);

        service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa");

        verify(notificationFacade).createNotification(any());
    }

    // --- listByStatus ---

    @Test
    void listByStatusOnlyReturnsStoresInThatStage() {
        service.transition(STORE, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, "admin");
        db.put(OTHER_STORE, storeAt(OTHER_STORE, BillingActivationStatus.AWAITING_PROVIDER));

        StoreBillingConfigRepository repository = mock(StoreBillingConfigRepository.class);
        when(repository.findByStatusOrderByUpdatedAtAsc(BillingActivationStatus.DOCUMENTS_SUBMITTED))
                .thenReturn(List.of(db.get(STORE)));
        StoreBillingConfigServiceImpl withRepo = new StoreBillingConfigServiceImpl(
                repository, new BillingActivationMapper(), tokenManager, notificationFacade, storeFacade);

        List<BillingActivationDto> queue = withRepo.listByStatus(BillingActivationStatus.DOCUMENTS_SUBMITTED);

        assertEquals(1, queue.size());
        assertEquals(STORE, queue.get(0).storeId());
    }

    private static StoreBillingConfig storeAt(Long storeId, BillingActivationStatus status) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(storeId);
        config.setStatus(status);
        return config;
    }

    // --- testConnection nunca lanza, siempre devuelve un resultado ---

    @Test
    void testConnectionReturnsSuccessWhenFactusAcceptsTheCredentials() {
        BillingConnectionTestResult result = service.testConnection(STORE, BillingEnvironment.SANDBOX);

        assertTrue(result.success());
        assertEquals(BillingEnvironment.SANDBOX, result.environment());
    }

    @Test
    void testConnectionReturnsFailureInsteadOfThrowingWhenFactusRejects() {
        doThrow(new FactusApiException(401, "credenciales inválidas", null))
                .when(tokenManager).verifyConnection(STORE, BillingEnvironment.PRODUCTION);

        BillingConnectionTestResult result = service.testConnection(STORE, BillingEnvironment.PRODUCTION);

        assertFalse(result.success());
        assertEquals("credenciales inválidas", result.message());
    }
}
