package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.BillingProviderException;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import com.sciencebot.pos.billing.internal.adapters.BillingOnboardingProvider;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.DianEnablementStatus;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.BillingActivationMapper;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigServiceImpl;
import com.sciencebot.pos.notifications.CreateNotificationCommand;
import com.sciencebot.pos.notifications.NotificationFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;

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
    private static final String ACTIVATION_LINK = "/invoicing?tab=activation";

    private final Map<Long, StoreBillingConfig> db = new HashMap<>();
    private ElectronicInvoicingProvider provider;
    private ActiveBillingProvider activeProvider;
    private StoreBillingConfigRepository repository;
    private NotificationFacade notificationFacade;
    private BillingOnboardingProvider onboarding;
    private PlatformTransactionManager transactionManager;
    /** La plataforma tiene el token del proveedor (sin él ninguna cuenta queda configurada). */
    private boolean platformToken = true;
    private StoreBillingConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(StoreBillingConfigRepository.class);
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<Long>getArgument(0))));
        // Las transiciones leen la fila con lock (probado con H2 real en StoreBillingActivationConcurrencyTest).
        when(repository.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<Long>getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> {
            StoreBillingConfig saved = inv.getArgument(0);
            db.put(saved.getStoreId(), saved);
            return saved;
        });
        provider = providerWhoseAccountsAreTheLoadedCompanyIds();
        activeProvider = new ActiveBillingProvider(List.of(provider), "alegra");
        notificationFacade = mock(NotificationFacade.class);
        onboarding = mock(BillingOnboardingProvider.class);
        when(onboarding.readEnablement(any(), any())).thenReturn(DianEnablementStatus.of("AUTHORIZED"));
        transactionManager = mock(PlatformTransactionManager.class);

        // Sin StoreFacade: habilitar la facturación ya no depende de los documentos del local (Alegra no los pide).
        service = new StoreBillingConfigServiceImpl(repository, new BillingActivationMapper(activeProvider), activeProvider,
                notificationFacade, onboarding, transactionManager);
    }

    /**
     * Proveedor "Alegra" simulado: la cuenta está configurada cuando el local ya tiene el company.id del entorno
     * (lo mismo que hace el adaptador real a través de su resolver).
     */
    private ElectronicInvoicingProvider providerWhoseAccountsAreTheLoadedCompanyIds() {
        ElectronicInvoicingProvider fake = mock(ElectronicInvoicingProvider.class);
        when(fake.getProviderName()).thenReturn("alegra");
        when(fake.displayName()).thenReturn("Alegra");
        when(fake.isAccountConfigured(any(), any())).thenAnswer(inv -> {
            StoreBillingConfig config = db.get(inv.<Long>getArgument(0));
            return platformToken && config != null
                    && config.companyIdFor(inv.<BillingEnvironment>getArgument(1)) != null;
        });
        when(fake.hasUsableInvoiceRange(any(), any())).thenReturn(true);
        return fake;
    }

    private StoreBillingConfigServiceImpl serviceWith(ElectronicInvoicingProvider other, String configuredName) {
        ActiveBillingProvider active = new ActiveBillingProvider(List.of(other), configuredName);
        return new StoreBillingConfigServiceImpl(repository, new BillingActivationMapper(active), active,
                notificationFacade, onboarding, transactionManager);
    }

    private BillingActivationDto loadCompanyId(BillingEnvironment environment) {
        return service.updateProviderCompanyId(STORE, environment, "company-1", "sa");
    }

    /** Lleva al local por el camino normal hasta el estado indicado. */
    private void moveTo(BillingActivationStatus target) {
        BillingActivationStatus[] path = {
                BillingActivationStatus.IN_SETUP,
                BillingActivationStatus.ACTIVE
        };
        for (BillingActivationStatus step : path) {
            service.transition(STORE, step, null, "sa");
            if (step == target) {
                return;
            }
        }
    }

    private CreateNotificationCommand lastNotification() {
        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationFacade).createNotification(captor.capture());
        return captor.getValue();
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
    void suspensionNeedsAReasonAndTheReasonIsClearedOnReactivation() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        service.transition(STORE, BillingActivationStatus.IN_SETUP, null, "admin");

        assertThrows(IllegalArgumentException.class,
                () -> service.transition(STORE, BillingActivationStatus.SUSPENDED, "  ", "sa"));

        BillingActivationDto dto = service.transition(STORE, BillingActivationStatus.SUSPENDED, "RUT ilegible", "sa");
        assertEquals(BillingActivationStatus.SUSPENDED, dto.status());
        assertEquals("RUT ilegible", dto.statusReason());

        BillingActivationDto reactivated = service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa");
        assertNull(reactivated.statusReason(), "el motivo se borra al salir de SUSPENDED");
    }

    @Test
    void cannotActivateWithoutCredentialsOfTheActiveEnvironment() {
        moveTo(BillingActivationStatus.IN_SETUP);

        assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        // Credenciales de producción no sirven mientras el entorno activo es SANDBOX.
        loadCompanyId(BillingEnvironment.PRODUCTION);
        assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        loadCompanyId(BillingEnvironment.SANDBOX);
        BillingActivationDto dto = service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa");
        assertEquals(BillingActivationStatus.ACTIVE, dto.status());
    }

    @Test
    void dtoNeverExposesTheCompanyIdOnlyWhetherEnvironmentIsConfigured() {
        BillingActivationDto dto =
                loadCompanyId(BillingEnvironment.PRODUCTION);

        assertTrue(dto.productionConfigured());
        assertFalse(dto.sandboxConfigured());
        assertNull(dto.productionNumberingRangeId());
        assertFalse(dto.toString().contains("company-1"));
    }

    @Test
    void credentialsAreKeptPerStore() {
        loadCompanyId(BillingEnvironment.SANDBOX);

        assertFalse(service.get(OTHER_STORE).sandboxConfigured());
        assertTrue(service.get(STORE).sandboxConfigured());
    }

    @Test
    void activeStoreCannotSwitchToAnEnvironmentWithoutCredentials() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.ACTIVE);

        assertThrows(IllegalStateException.class,
                () -> service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "sa"));

        loadCompanyId(BillingEnvironment.PRODUCTION);
        assertEquals(BillingEnvironment.PRODUCTION,
                service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "sa").environment());
    }

    @Test
    void recordsWhoMadeTheLastChange() {
        service.transition(STORE, BillingActivationStatus.IN_SETUP, null, "admin@local.co");
        assertEquals("admin@local.co", db.get(STORE).getUpdatedBy());

        service.transition(STORE, BillingActivationStatus.SUSPENDED, "Datos fiscales incompletos", "super@platform.co");
        assertEquals("super@platform.co", db.get(STORE).getUpdatedBy());
    }

    // --- cargar el company.id invalida los datos en caché del proveedor ---

    @Test
    void loadingTheCompanyIdInvalidatesTheCachedProviderData() {
        loadCompanyId(BillingEnvironment.SANDBOX);

        verify(provider).onAccountChanged(STORE);
    }

    // --- requestActivation (lo origina el local) ---

    @Test
    void requestActivationMovesToInSetupAndDoesNotNotifyTheStoreItself() {
        BillingActivationDto dto = service.requestActivation(STORE, "admin@local.co");

        assertEquals(BillingActivationStatus.IN_SETUP, dto.status());
        assertEquals(BillingActivationStatus.IN_SETUP, db.get(STORE).getStatus());
        assertEquals("admin@local.co", db.get(STORE).getUpdatedBy());
        verifyNoInteractions(notificationFacade);
    }

    @Test
    void requestActivationNeedsNoUploadedDocuments() {
        // El servicio ya no recibe StoreFacade: no hay forma de que consulte los documentos del local.
        assertTrue(db.isEmpty());

        BillingActivationDto dto = service.requestActivation(STORE, "admin@local.co");

        assertEquals(BillingActivationStatus.IN_SETUP, dto.status());
    }

    @Test
    void requestActivationIsIdempotentWhileInSetup() {
        service.requestActivation(STORE, "admin@local.co");

        BillingActivationDto again = service.requestActivation(STORE, "otro@local.co");

        assertEquals(BillingActivationStatus.IN_SETUP, again.status());
        assertEquals("admin@local.co", db.get(STORE).getUpdatedBy(), "repetir la solicitud no cambia nada");
        verifyNoInteractions(notificationFacade);
    }

    @Test
    void requestActivationCannotMoveAnActiveOrSuspendedStoreBackToSetup() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.ACTIVE);

        assertThrows(IllegalStateException.class, () -> service.requestActivation(STORE, "admin@local.co"));

        service.transition(STORE, BillingActivationStatus.SUSPENDED, "Mora en el pago", "sa");
        assertThrows(IllegalStateException.class, () -> service.requestActivation(STORE, "admin@local.co"));
        assertEquals(BillingActivationStatus.SUSPENDED, db.get(STORE).getStatus());
    }

    // --- transition SÍ notifica al local (lo origina el super admin), salvo IN_SETUP ---

    @Test
    void everyTransitionReadsTheStoreConfigWithTheWriteLock() {
        service.transition(STORE, BillingActivationStatus.IN_SETUP, null, "sa");

        verify(repository).findByIdForUpdate(STORE);
    }

    @Test
    void movingToInSetupDoesNotNotifyTheStore() {
        service.transition(STORE, BillingActivationStatus.IN_SETUP, null, "sa");

        verifyNoInteractions(notificationFacade);
    }

    @Test
    void suspensionNotificationIncludesTheReasonAndLinksToTheActivationTab() {
        service.transition(STORE, BillingActivationStatus.IN_SETUP, null, "admin");
        reset(notificationFacade);

        service.transition(STORE, BillingActivationStatus.SUSPENDED, "RUT ilegible", "sa");

        CreateNotificationCommand command = lastNotification();
        assertEquals(STORE, command.storeId());
        assertEquals("BILLING_STATUS_CHANGED", command.type());
        assertEquals("Facturación electrónica suspendida", command.title());
        assertTrue(command.message().contains("RUT ilegible"));
        assertEquals(ACTIVATION_LINK, command.linkPath());
    }

    @Test
    void activationNotifiesTheStoreAndLinksToTheActivationTab() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        service.transition(STORE, BillingActivationStatus.IN_SETUP, null, "admin");
        reset(notificationFacade);

        service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa");

        CreateNotificationCommand command = lastNotification();
        assertEquals("BILLING_STATUS_CHANGED", command.type());
        assertEquals("Facturación electrónica activa", command.title());
        assertEquals(ACTIVATION_LINK, command.linkPath());
    }

    // --- listByStatus ---

    @Test
    void listByStatusOnlyReturnsStoresInThatStage() {
        service.transition(STORE, BillingActivationStatus.IN_SETUP, null, "admin");
        db.put(OTHER_STORE, storeAt(OTHER_STORE, BillingActivationStatus.ACTIVE));

        repository = mock(StoreBillingConfigRepository.class);
        when(repository.findByStatusOrderByUpdatedAtAsc(BillingActivationStatus.IN_SETUP))
                .thenReturn(List.of(db.get(STORE)));
        StoreBillingConfigServiceImpl withRepo = new StoreBillingConfigServiceImpl(
                repository, new BillingActivationMapper(activeProvider), activeProvider, notificationFacade,
                onboarding, transactionManager);

        List<BillingActivationDto> queue = withRepo.listByStatus(BillingActivationStatus.IN_SETUP);

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
    void testConnectionReturnsSuccessWhenTheProviderAcceptsTheCredentials() {
        BillingConnectionTestResult result = service.testConnection(STORE, BillingEnvironment.SANDBOX);

        assertTrue(result.success());
        assertEquals(BillingEnvironment.SANDBOX, result.environment());
    }

    @Test
    void testConnectionReturnsFailureInsteadOfThrowingWhenTheProviderRejects() {
        doThrow(new AlegraApiException(401, "credenciales inválidas", null))
                .when(provider).verifyConnection(STORE, BillingEnvironment.PRODUCTION);

        BillingConnectionTestResult result = service.testConnection(STORE, BillingEnvironment.PRODUCTION);

        assertFalse(result.success());
        assertEquals("credenciales inválidas", result.message());
    }

    @Test
    void testConnectionSuccessMessageNamesTheActiveProvider() {
        BillingConnectionTestResult result = service.testConnection(STORE, BillingEnvironment.SANDBOX);

        assertEquals("Conexión exitosa con Alegra (SANDBOX)", result.message());
        verify(provider).verifyConnection(STORE, BillingEnvironment.SANDBOX);
    }

    @Test
    void testConnectionCatchesAnyProviderException() {
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
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.ACTIVE);

        verify(provider, atLeastOnce()).isAccountConfigured(STORE, BillingEnvironment.SANDBOX);

        assertThrows(IllegalStateException.class,
                () -> service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "sa"));
        verify(provider, atLeastOnce()).isAccountConfigured(STORE, BillingEnvironment.PRODUCTION);
    }

    @Test
    void configuredFlagsInTheDtoComeFromTheProvider() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        when(provider.isAccountConfigured(STORE, BillingEnvironment.PRODUCTION)).thenReturn(true);

        BillingActivationDto dto = service.get(STORE);

        assertTrue(dto.sandboxConfigured());
        assertTrue(dto.productionConfigured(), "el flag sale del proveedor, no de las columnas de la entidad");
    }

    @Test
    void withTheMockProviderTheStoreCanBeActivatedWithoutLoadingCredentials() {
        ElectronicInvoicingProvider mockProvider = mock(ElectronicInvoicingProvider.class);
        when(mockProvider.getProviderName()).thenReturn("mock");
        when(mockProvider.displayName()).thenReturn("Mock");
        when(mockProvider.isAccountConfigured(any(), any())).thenReturn(true);
        when(mockProvider.hasUsableInvoiceRange(any(), any())).thenReturn(true);
        StoreBillingConfigServiceImpl mockService = serviceWith(mockProvider, "mock");
        mockService.transition(STORE, BillingActivationStatus.IN_SETUP, null, "admin");

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
        assertTrue(ex.getMessage().contains("alegra"));
    }

    // --- guarda de ACTIVE: empresa, habilitación DIAN y numeración de facturas, cada una con su mensaje ---

    private void dianSays(String value) {
        when(onboarding.readEnablement(any(), any())).thenReturn(DianEnablementStatus.of(value));
    }

    @Test
    void activationWithoutTheStoreCompanySaysSoAndDoesNotAskTheDian() {
        moveTo(BillingActivationStatus.IN_SETUP);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        assertTrue(ex.getMessage().contains("empresa"), ex.getMessage());
        assertFalse(ex.getMessage().contains("credenciales"), ex.getMessage());
        assertFalse(ex.getMessage().contains("SANDBOX"), "el entorno va en español: " + ex.getMessage());
        verifyNoInteractions(onboarding);
        assertEquals(BillingActivationStatus.IN_SETUP, db.get(STORE).getStatus());
    }

    @Test
    void activationWithoutThePlatformTokenSaysItIsThePlatform() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.IN_SETUP);
        platformToken = false;

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        assertTrue(ex.getMessage().contains("plataforma"), ex.getMessage());
    }

    @Test
    void activationNeedsTheDianAuthorization() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.IN_SETUP);

        dianSays(null);
        IllegalStateException notStarted = assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));
        assertTrue(notStarted.getMessage().contains("set de pruebas"), notStarted.getMessage());

        dianSays("PENDING");
        IllegalStateException pending = assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));
        assertTrue(pending.getMessage().contains("DIAN"), pending.getMessage());

        assertEquals(BillingActivationStatus.IN_SETUP, db.get(STORE).getStatus());
        verify(onboarding, atLeastOnce()).readEnablement(BillingEnvironment.SANDBOX, "company-1");
    }

    @Test
    void activationNeedsAUsableInvoiceRangeInTheStoreEnvironment() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.IN_SETUP);
        when(provider.hasUsableInvoiceRange(STORE, BillingEnvironment.SANDBOX)).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        assertTrue(ex.getMessage().contains("numeración"), ex.getMessage());
        assertEquals(BillingActivationStatus.IN_SETUP, db.get(STORE).getStatus());
    }

    @Test
    void reactivatingASuspendedStoreChecksTheSameGuards() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.ACTIVE);
        service.transition(STORE, BillingActivationStatus.SUSPENDED, "Mora en el pago", "sa");
        dianSays("PENDING");

        assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));
        assertEquals(BillingActivationStatus.SUSPENDED, db.get(STORE).getStatus());

        dianSays("AUTHORIZED");
        assertEquals(BillingActivationStatus.ACTIVE,
                service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa").status());
    }

    @Test
    void theDianIsAskedBeforeOpeningTheTransaction() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.IN_SETUP);
        clearInvocations(transactionManager);

        service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa");

        InOrder order = inOrder(onboarding, transactionManager);
        order.verify(onboarding).readEnablement(BillingEnvironment.SANDBOX, "company-1");
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
    }

    @Test
    void aDianLookupFailureBlocksTheActivationWithAClearMessage() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.IN_SETUP);
        when(onboarding.readEnablement(any(), any())).thenThrow(new AlegraApiException(0,
                "No se pudo consultar la habilitación ante la DIAN en Alegra: no se pudo conectar con Alegra", null));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        assertTrue(ex.getMessage().contains("no se pudo conectar con Alegra"), ex.getMessage());
        assertEquals(BillingActivationStatus.IN_SETUP, db.get(STORE).getStatus());
    }

    @Test
    void anInvalidTransitionNeverCallsTheProvider() {
        assertThrows(IllegalStateException.class,
                () -> service.transition(STORE, BillingActivationStatus.ACTIVE, null, "sa"));

        verifyNoInteractions(onboarding);
    }

    // --- cambio de entorno: empresa y numeración en el destino, en cualquier estado, y aviso al local ---

    @Test
    void aStoreInSetupCannotSwitchToAnEnvironmentWithoutItsCompany() {
        moveTo(BillingActivationStatus.IN_SETUP);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "sa"));

        assertTrue(ex.getMessage().contains("producción"), ex.getMessage());
        assertEquals(BillingEnvironment.SANDBOX, db.get(STORE).getEnvironment());
    }

    @Test
    void theTargetEnvironmentNeedsAUsableInvoiceRange() {
        loadCompanyId(BillingEnvironment.PRODUCTION);
        when(provider.hasUsableInvoiceRange(STORE, BillingEnvironment.PRODUCTION)).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "sa"));

        assertTrue(ex.getMessage().contains("numeración"), ex.getMessage());
        assertEquals(BillingEnvironment.SANDBOX, db.get(STORE).getEnvironment());
        verifyNoInteractions(notificationFacade);
    }

    @Test
    void switchingEnvironmentNotifiesTheStoreWhichOneIsActive() {
        loadCompanyId(BillingEnvironment.PRODUCTION);
        loadCompanyId(BillingEnvironment.SANDBOX);

        service.switchEnvironment(STORE, BillingEnvironment.PRODUCTION, "sa");
        CreateNotificationCommand toProduction = lastNotification();
        assertEquals("BILLING_STATUS_CHANGED", toProduction.type());
        assertEquals(ACTIVATION_LINK, toProduction.linkPath());
        assertTrue(toProduction.message().contains("producción"), toProduction.message());

        reset(notificationFacade);
        service.switchEnvironment(STORE, BillingEnvironment.SANDBOX, "sa");
        CreateNotificationCommand toSandbox = lastNotification();
        assertTrue(toSandbox.message().contains("pruebas"), toSandbox.message());
        assertTrue(toSandbox.message().contains("de prueba"), "las facturas del sandbox son de prueba");
    }

    @Test
    void switchingToTheSameEnvironmentDoesNotNotify() {
        loadCompanyId(BillingEnvironment.SANDBOX);

        service.switchEnvironment(STORE, BillingEnvironment.SANDBOX, "sa");

        verifyNoInteractions(notificationFacade);
    }

    // --- startSelfServiceSetup: el asistente deja al local en configuración y en producción ---

    @Test
    void selfServiceSetupCreatesTheRowInSetupAndInProductionWithoutNotifying() {
        BillingActivationDto dto = service.startSelfServiceSetup(STORE, "admin@local.co");

        assertEquals(BillingActivationStatus.IN_SETUP, dto.status());
        assertEquals(BillingEnvironment.PRODUCTION, db.get(STORE).getEnvironment());
        assertEquals("admin@local.co", db.get(STORE).getUpdatedBy());
        verifyNoInteractions(notificationFacade, onboarding);
    }

    @Test
    void selfServiceSetupMovesAStoreInSetupToProduction() {
        moveTo(BillingActivationStatus.IN_SETUP);

        BillingActivationDto dto = service.startSelfServiceSetup(STORE, "admin@local.co");

        assertEquals(BillingActivationStatus.IN_SETUP, dto.status());
        assertEquals(BillingEnvironment.PRODUCTION, dto.environment());
    }

    @Test
    void selfServiceSetupNeverTouchesAnActiveStore() {
        loadCompanyId(BillingEnvironment.SANDBOX);
        moveTo(BillingActivationStatus.ACTIVE);

        BillingActivationDto dto = service.startSelfServiceSetup(STORE, "admin@local.co");

        assertEquals(BillingActivationStatus.ACTIVE, dto.status());
        assertEquals(BillingEnvironment.SANDBOX, dto.environment());
    }

    @Test
    void selfServiceSetupKeepsASuspendedStoreSuspendedAndInItsEnvironment() {
        service.transition(STORE, BillingActivationStatus.IN_SETUP, null, "admin");
        service.transition(STORE, BillingActivationStatus.SUSPENDED, "Mora en el pago", "sa");
        BillingEnvironment before = db.get(STORE).getEnvironment();

        BillingActivationDto dto = service.startSelfServiceSetup(STORE, "admin@local.co");

        assertEquals(BillingActivationStatus.SUSPENDED, dto.status());
        assertEquals(before, dto.environment(), "suspendido: el entorno solo lo cambia el back office con su guarda");
        assertEquals(BillingEnvironment.SANDBOX, dto.environment());
    }
}
