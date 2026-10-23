package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.UpdateBillingCredentialsCommand;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.BillingProviderException;
import com.sciencebot.pos.billing.exception.FactusApiException;
import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
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
import static org.mockito.Mockito.*;

class StoreBillingConfigServiceImplTest {

    private static final Long STORE = 7L;
    private static final Long OTHER_STORE = 8L;

    private final Map<Long, StoreBillingConfig> db = new HashMap<>();
    private ElectronicInvoicingProvider provider;
    private ActiveBillingProvider activeProvider;
    private StoreBillingConfigRepository repository;
    private NotificationFacade notificationFacade;
    private StoreFacade storeFacade;
    private StoreBillingConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(StoreBillingConfigRepository.class);
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<Long>getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> {
            StoreBillingConfig saved = inv.getArgument(0);
            db.put(saved.getStoreId(), saved);
            return saved;
        });
        provider = providerWhoseAccountsAreTheStoredFactusCredentials();
        activeProvider = new ActiveBillingProvider(List.of(provider), "factus");
        notificationFacade = mock(NotificationFacade.class);
        storeFacade = mock(StoreFacade.class);
        // Por defecto el local ya tiene documentos subidos (no es lo que prueban estos tests).
        when(storeFacade.getDocuments(any())).thenReturn(
                List.of(new StoreDocumentDto(1L, STORE, "RUT", "url", "PENDING", null, null, null)));

        service = new StoreBillingConfigServiceImpl(repository, new BillingActivationMapper(activeProvider), activeProvider,
                notificationFacade, storeFacade);
    }

    /**
     * Proveedor "Factus" simulado: la cuenta está configurada cuando el local guardó credenciales
     * completas del entorno (lo mismo que hace el adaptador real a través de su resolver).
     */
    private ElectronicInvoicingProvider providerWhoseAccountsAreTheStoredFactusCredentials() {
        ElectronicInvoicingProvider fake = mock(ElectronicInvoicingProvider.class);
        when(fake.getProviderName()).thenReturn("factus");
        when(fake.displayName()).thenReturn("Factus");
        when(fake.isAccountConfigured(any(), any())).thenAnswer(inv -> {
            StoreBillingConfig config = db.get(inv.<Long>getArgument(0));
            return config != null && config.credentialsFor(inv.<BillingEnvironment>getArgument(1)).isComplete();
        });
        return fake;
    }

    private StoreBillingConfigServiceImpl serviceWith(ElectronicInvoicingProvider other, String configuredName) {
        ActiveBillingProvider active = new ActiveBillingProvider(List.of(other), configuredName);
        return new StoreBillingConfigServiceImpl(repository, new BillingActivationMapper(active), active,
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

        verify(provider).onAccountChanged(STORE);
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

        repository = mock(StoreBillingConfigRepository.class);
        when(repository.findByStatusOrderByUpdatedAtAsc(BillingActivationStatus.DOCUMENTS_SUBMITTED))
                .thenReturn(List.of(db.get(STORE)));
        StoreBillingConfigServiceImpl withRepo = new StoreBillingConfigServiceImpl(
                repository, new BillingActivationMapper(activeProvider), activeProvider, notificationFacade, storeFacade);

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
                .when(provider).verifyConnection(STORE, BillingEnvironment.PRODUCTION);

        BillingConnectionTestResult result = service.testConnection(STORE, BillingEnvironment.PRODUCTION);

        assertFalse(result.success());
        assertEquals("credenciales inválidas", result.message());
    }

    @Test
    void testConnectionSuccessMessageNamesTheActiveProvider() {
        BillingConnectionTestResult result = service.testConnection(STORE, BillingEnvironment.SANDBOX);

        assertEquals("Conexión exitosa con Factus (SANDBOX)", result.message());
        verify(provider).verifyConnection(STORE, BillingEnvironment.SANDBOX);
    }

    @Test
    void testConnectionCatchesAnyProviderExceptionNotJustFactus() {
        doThrow(new BillingProviderException(0, "sin conexión con el proveedor", null))
                .when(provider).verifyConnection(STORE, BillingEnvironment.SANDBOX);

        BillingConnectionTestResult result = service.testConnection(STORE, BillingEnvironment.SANDBOX);

        assertFalse(result.success());
        assertEquals("sin conexión con el proveedor", result.message());
    }

    @Test
    void testConnectionStillThrowsWhenTheStoreHasNoCredentialsToTest() {
        doThrow(new BillingNotEnabledException("Faltan credenciales"))
                .when(provider).verifyConnection(STORE, BillingEnvironment.SANDBOX);

        assertThrows(BillingNotEnabledException.class,
                () -> service.testConnection(STORE, BillingEnvironment.SANDBOX));
    }

    // --- el servicio solo habla con el puerto del proveedor activo ---

    @Test
    void activationAndEnvironmentSwitchAskTheProviderIfTheAccountIsConfigured() {
        service.updateCredentials(STORE, BillingEnvironment.SANDBOX, fullCredentials(), "sa");
        moveTo(BillingActivationStatus.ACTIVE);

        verify(provider, atLeastOnce()).isAccountConfigured(STORE, BillingEnvironment.SANDBOX);

        assertThrows(IllegalStateException.class,
                () -> service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "sa"));
        verify(provider, atLeastOnce()).isAccountConfigured(STORE, BillingEnvironment.PRODUCTION);
    }

    @Test
    void configuredFlagsInTheDtoComeFromTheProvider() {
        service.updateCredentials(STORE, BillingEnvironment.SANDBOX, fullCredentials(), "sa");
        when(provider.isAccountConfigured(STORE, BillingEnvironment.PRODUCTION)).thenReturn(true);

        BillingActivationDto dto = service.get(STORE);

        assertTrue(dto.sandboxConfigured());
        assertTrue(dto.productionConfigured(), "el flag sale del proveedor, no de las columnas de la entidad");
    }

    @Test
    void awaitingProviderNotificationNamesTheActiveProvider() {
        service.transition(STORE, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, "admin");
        reset(notificationFacade);
        when(provider.displayName()).thenReturn("Alegra");

        service.transition(STORE, BillingActivationStatus.AWAITING_PROVIDER, null, "sa");

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationFacade).createNotification(captor.capture());
        assertTrue(captor.getValue().message().contains("Alegra"));
        assertFalse(captor.getValue().message().contains("Factus"));
    }

    @Test
    void withTheMockProviderTheStoreCanBeActivatedWithoutLoadingCredentials() {
        ElectronicInvoicingProvider mockProvider = mock(ElectronicInvoicingProvider.class);
        when(mockProvider.getProviderName()).thenReturn("mock");
        when(mockProvider.displayName()).thenReturn("Mock");
        when(mockProvider.isAccountConfigured(any(), any())).thenReturn(true);
        StoreBillingConfigServiceImpl mockService = serviceWith(mockProvider, "mock");
        mockService.transition(STORE, BillingActivationStatus.DOCUMENTS_SUBMITTED, null, "admin");
        mockService.transition(STORE, BillingActivationStatus.AWAITING_PROVIDER, null, "sa");

        BillingActivationDto dto = mockService.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa");

        assertEquals(BillingActivationStatus.ACTIVE, dto.status());
        assertEquals("Conexión exitosa con Mock (SANDBOX)",
                mockService.testConnection(STORE, BillingEnvironment.SANDBOX).message());
    }

    @Test
    void anUnknownConfiguredProviderFailsWithTheAvailableOnes() {
        StoreBillingConfigServiceImpl misconfigured = serviceWith(provider, "siigo");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> misconfigured.testConnection(STORE, BillingEnvironment.SANDBOX));

        assertTrue(ex.getMessage().contains("siigo"));
        assertTrue(ex.getMessage().contains("factus"));
    }
}
