package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.BillingSetupDto;
import com.sciencebot.pos.billing.dto.BillingSetupStepDto;
import com.sciencebot.pos.billing.dto.BillingSetupStepDto.Key;
import com.sciencebot.pos.billing.dto.BillingSetupStepDto.State;
import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.FiscalProfileRequest;
import com.sciencebot.pos.billing.dto.NumberingImportResultDto;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import com.sciencebot.pos.billing.internal.adapters.BillingOnboardingProvider;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import com.sciencebot.pos.billing.internal.adapters.mock.MockOnboardingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.CompanyProfileData;
import com.sciencebot.pos.billing.internal.adapters.dto.DianEnablementStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.DianResolutionData;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyProfile;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyRegistration;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.BillingSetupMapper;
import com.sciencebot.pos.billing.internal.mappers.DianResolutionMapper;
import com.sciencebot.pos.billing.internal.repositories.BillingCompanyProfileRepository;
import com.sciencebot.pos.billing.internal.repositories.BillingCompanyRegistrationRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.stores.StoreDto;
import com.sciencebot.pos.stores.StoreFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Asistente de habilitación en autoservicio: datos fiscales → empresa en producción, set de pruebas de la DIAN,
 * importación de la numeración y activación automática. El proveedor se llama siempre fuera de las transacciones.
 */
class BillingSetupServiceImplTest {

    private static final Long STORE = 9L;
    private static final String ADMIN = "admin@tienda.co";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final BillingEnvironment PROD = BillingEnvironment.PRODUCTION;
    private static final String TEST_SET = "d7b8f2c4-1a2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final String PLATFORM_NOT_READY =
            "La facturación electrónica aún no está disponible en la plataforma. Guardamos tus datos y te avisaremos.";

    private final Map<Long, StoreBillingConfig> configs = new HashMap<>();
    private final Map<Long, BillingCompanyProfile> profiles = new HashMap<>();

    private StoreBillingConfigService configService;
    private BillingOnboardingProvider onboarding;
    private ElectronicInvoicingProvider provider;
    private NumberingRangeService rangeService;
    private StoreFacade storeFacade;
    private PlatformTransactionManager transactionManager;
    private BillingSetupServiceImpl service;

    @BeforeEach
    void setUp() {
        StoreBillingConfigRepository configRepository = mock(StoreBillingConfigRepository.class);
        when(configRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(configs.get(inv.<Long>getArgument(0))));
        BillingCompanyProfileRepository profileRepository = mock(BillingCompanyProfileRepository.class);
        when(profileRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(profiles.get(inv.<Long>getArgument(0))));
        when(profileRepository.save(any())).thenAnswer(inv -> {
            BillingCompanyProfile saved = inv.getArgument(0);
            profiles.put(saved.getStoreId(), saved);
            return saved;
        });

        configService = mock(StoreBillingConfigService.class);
        // Lo que hacen los métodos reales sobre la fila del local (probados en StoreBillingConfigServiceImplTest).
        doAnswer(inv -> {
            StoreBillingConfig config = configs.computeIfAbsent(STORE, this::newConfig);
            if (config.getStatus() == BillingActivationStatus.NOT_REQUESTED) {
                config.setStatus(BillingActivationStatus.IN_SETUP);
            }
            if (config.getStatus() != BillingActivationStatus.ACTIVE) {
                config.setEnvironment(PROD);
            }
            return null;
        }).when(configService).startSelfServiceSetup(eq(STORE), anyString());
        doAnswer(inv -> {
            configs.get(STORE).assignCompanyId(inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(configService).updateProviderCompanyId(eq(STORE), any(), anyString(), anyString());
        doAnswer(inv -> {
            configs.get(STORE).setStatus(inv.getArgument(1));
            return null;
        }).when(configService).transition(eq(STORE), any(), any(), anyString());

        onboarding = mock(BillingOnboardingProvider.class);
        when(onboarding.isPlatformReady(any())).thenReturn(true);
        when(onboarding.readEnablement(any(), any())).thenReturn(DianEnablementStatus.of(null));

        provider = mock(ElectronicInvoicingProvider.class);
        when(provider.getProviderName()).thenReturn("alegra");

        rangeService = mock(NumberingRangeService.class);
        when(rangeService.listNumberingRanges()).thenReturn(List.of());

        storeFacade = mock(StoreFacade.class);
        when(storeFacade.getById(STORE)).thenReturn(store("900123456-8"));

        transactionManager = mock(PlatformTransactionManager.class);
        BillingCompanyRegistrationRepository registrationsRepository = mock(BillingCompanyRegistrationRepository.class);
        Map<Long, BillingCompanyRegistration> registrations = new HashMap<>();
        when(registrationsRepository.findByStoreIdAndEnvironmentAndProviderName(any(), any(), any()))
                .thenAnswer(inv -> Optional.ofNullable(registrations.get(inv.<Long>getArgument(0))));
        when(registrationsRepository.saveAndFlush(any())).thenAnswer(inv -> {
            BillingCompanyRegistration saved = inv.getArgument(0);
            registrations.put(saved.getStoreId(), saved);
            return saved;
        });
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.find(eq(StoreBillingConfig.class), any(), eq(LockModeType.PESSIMISTIC_WRITE)))
                .thenAnswer(inv -> configs.get(inv.<Long>getArgument(1)));
        @SuppressWarnings("unchecked") TypedQuery<Long> companyCount = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(companyCount);
        when(companyCount.setParameter(anyString(), any())).thenReturn(companyCount);
        when(companyCount.getSingleResult()).thenReturn(0L);
        BillingCompanyRegistrationTransactions registrationTransactions = new BillingCompanyRegistrationTransactions(
                registrationsRepository, entityManager);

        service = new BillingSetupServiceImpl(configService, configRepository, profileRepository, onboarding,
                new ActiveBillingProvider(List.of(provider), "alegra"), rangeService, storeFacade,
                new BillingSetupMapper(), new DianResolutionMapper(), transactionManager,
                Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()),
                registrationTransactions);
        TenantContext.setStoreId(STORE);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- datos de prueba ---

    private StoreBillingConfig newConfig(Long storeId) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(storeId);
        return config;
    }

    private static StoreDto store(String taxId) {
        return new StoreDto(STORE, "Tienda La Esquina", 1L, "Minimercado", "3001234567", "hola@esquina.co", null,
                "Calle 10 # 5-20", taxId, "ACTIVE", true, null, null, "BASIC",
                "RETAIL", "DEFAULT", java.util.List.of("RETAIL"), java.util.List.of());
    }

    private static FiscalProfileRequest request() {
        return new FiscalProfileRequest("Tienda La Esquina SAS", "La Esquina", "900123456", "8", 1, "O-13;O-47",
                "4711", "contabilidad@esquina.co", "3001234567", "Calle 10 # 5-20", "05001", "05");
    }

    /** Local con perfil guardado y empresa creada en producción. */
    private void storeWithCompany(BillingActivationStatus status) {
        StoreBillingConfig config = newConfig(STORE);
        config.setStatus(status);
        config.setEnvironment(PROD);
        config.assignCompanyId(PROD, "co-1");
        configs.put(STORE, config);
        BillingCompanyProfile profile = new BillingCompanyProfile();
        profile.setStoreId(STORE);
        profile.setLegalName("Tienda La Esquina SAS");
        profile.setNit("900123456");
        profile.setDv("8");
        profile.setOrganizationType(1);
        profile.setRegimeCode("O-13");
        profile.setEmail("a@b.co");
        profile.setAddress("Calle 1");
        profile.setCityCode("05001");
        profile.setDepartmentCode("05");
        profiles.put(STORE, profile);
    }

    private void dianSays(String value) {
        when(onboarding.readEnablement(PROD, "co-1")).thenReturn(DianEnablementStatus.of(value));
    }

    private static BillingSetupStepDto step(BillingSetupDto dto, Key key) {
        return dto.steps().stream().filter(s -> s.key() == key).findFirst().orElseThrow();
    }

    private static DianResolutionData resolution(String prefix, String number, LocalDate start, LocalDate end) {
        return new DianResolutionData(number, prefix, 1L, 5000L, start, end, "tk-" + number);
    }

    private static NumberingRangeDto range(String document, String prefix, String resolution, boolean active) {
        return new NumberingRangeDto(1L, document, prefix, resolution, 1L, 5000L, 1L, null, null, null, active);
    }

    // --- GET /billing/setup ---

    @Test
    void newStoreSeesTheFiscalStepFirstWithDataSuggestedFromTheStore() {
        BillingSetupDto dto = service.getSetup(STORE);

        assertEquals(BillingActivationStatus.NOT_REQUESTED, dto.status());
        assertTrue(dto.platformReady());
        assertEquals(List.of(Key.FISCAL_DATA, Key.DIAN_ENABLEMENT, Key.NUMBERING, Key.READY),
                dto.steps().stream().map(BillingSetupStepDto::key).toList());
        assertEquals(List.of(State.CURRENT, State.PENDING, State.PENDING, State.PENDING),
                dto.steps().stream().map(BillingSetupStepDto::state).toList());
        dto.steps().forEach(s -> assertFalse(s.message() == null || s.message().isBlank(), s.key() + " sin mensaje"));
        assertFalse(dto.profile().saved());
        assertEquals("Tienda La Esquina", dto.profile().legalName());
        assertEquals("900123456", dto.profile().nit());
        assertEquals("8", dto.profile().dv());
        assertEquals("hola@esquina.co", dto.profile().email());
        assertEquals("3001234567", dto.profile().phone());
        assertEquals("Calle 10 # 5-20", dto.profile().address());
        assertEquals(0, dto.invoiceRangesCount());
        verify(onboarding, never()).readEnablement(any(), any());
        assertTrue(configs.isEmpty(), "leer el asistente no crea la fila del local");
    }

    @Test
    void theSuggestedCheckDigitIsComputedWhenTheStoreTaxIdHasNone() {
        when(storeFacade.getById(STORE)).thenReturn(store("900123456"));

        assertEquals("8", service.getSetup(STORE).profile().dv());
    }

    @Test
    void savedFiscalDataIsShownInsteadOfTheSuggestion() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);

        BillingSetupDto dto = service.getSetup(STORE);

        assertTrue(dto.profile().saved());
        assertEquals("Tienda La Esquina SAS", dto.profile().legalName());
        verify(storeFacade, never()).getById(any());
    }

    @Test
    void activeStoresAreNotPolledAtTheProvider() {
        storeWithCompany(BillingActivationStatus.ACTIVE);
        when(provider.hasUsableInvoiceRange(STORE, PROD)).thenReturn(true);

        BillingSetupDto dto = service.getSetup(STORE);

        verify(onboarding, never()).readEnablement(any(), any());
        assertEquals(List.of(State.DONE, State.DONE, State.DONE, State.DONE),
                dto.steps().stream().map(BillingSetupStepDto::state).toList());
    }

    @Test
    void anAuthorizedStoreWithAUsableInvoiceRangeIsActivatedBySystem() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        when(provider.hasUsableInvoiceRange(STORE, PROD)).thenReturn(true);
        when(provider.countInvoiceRanges(STORE, PROD)).thenReturn(1);

        BillingSetupDto dto = service.getSetup(STORE);

        verify(configService).transition(STORE, BillingActivationStatus.ACTIVE, null, "system");
        assertEquals(BillingActivationStatus.ACTIVE, dto.status());
        assertEquals(State.DONE, step(dto, Key.READY).state());
        assertEquals(1, dto.invoiceRangesCount());
    }

    @Test
    void anAuthorizedStoreWithoutRangeIsNotActivatedAndMustImportItsNumbering() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");

        BillingSetupDto dto = service.getSetup(STORE);

        verify(configService, never()).transition(any(), any(), any(), any());
        assertEquals(State.DONE, step(dto, Key.DIAN_ENABLEMENT).state());
        assertEquals(State.CURRENT, step(dto, Key.NUMBERING).state());
        assertEquals(State.PENDING, step(dto, Key.READY).state());
    }

    @Test
    void aRangeWithoutDianAuthorizationDoesNotActivateTheStore() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("PENDING");
        when(provider.hasUsableInvoiceRange(STORE, PROD)).thenReturn(true);

        BillingSetupDto dto = service.getSetup(STORE);

        verify(configService, never()).transition(any(), any(), any(), any());
        assertEquals(State.CURRENT, step(dto, Key.DIAN_ENABLEMENT).state());
        assertTrue(step(dto, Key.DIAN_ENABLEMENT).message().contains("revisando"));
    }

    @Test
    void whenAnotherRequestActivatedTheStoreFirstTheWizardShowsItActiveWithoutAnError() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        when(provider.hasUsableInvoiceRange(STORE, PROD)).thenReturn(true);
        // La otra solicitud ganó el lock: cuando esta llega, el local ya está ACTIVE y la transición se rechaza.
        doAnswer(inv -> {
            configs.get(STORE).setStatus(BillingActivationStatus.ACTIVE);
            throw new IllegalStateException("No se puede pasar la facturación de ACTIVE a ACTIVE");
        }).when(configService).transition(eq(STORE), eq(BillingActivationStatus.ACTIVE), any(), anyString());

        BillingSetupDto dto = service.getSetup(STORE);

        assertEquals(BillingActivationStatus.ACTIVE, dto.status());
        assertEquals(State.DONE, step(dto, Key.READY).state());
        assertTrue(dto.steps().stream().noneMatch(step -> step.state() == State.ERROR),
                "perder la carrera no es un error del dueño");
    }

    @Test
    void anActivationRefusalIsShownOnTheReadyStep() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        when(provider.hasUsableInvoiceRange(STORE, PROD)).thenReturn(true);
        doThrow(new IllegalStateException("La plataforma aún no tiene acceso al proveedor"))
                .when(configService).transition(eq(STORE), eq(BillingActivationStatus.ACTIVE), any(), anyString());

        BillingSetupDto dto = service.getSetup(STORE);

        assertEquals(BillingActivationStatus.IN_SETUP, dto.status());
        assertEquals(State.ERROR, step(dto, Key.READY).state());
        assertEquals("La plataforma aún no tiene acceso al proveedor", step(dto, Key.READY).message());
    }

    @Test
    void aDianAnswerOtherThanPendingIsAnError() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("REJECTED");

        BillingSetupStepDto dian = step(service.getSetup(STORE), Key.DIAN_ENABLEMENT);

        assertEquals(State.ERROR, dian.state());
        assertTrue(dian.message().contains("REJECTED"), dian.message());
    }

    @Test
    void aSuspendedStoreSeesWhyOnTheReadyStep() {
        storeWithCompany(BillingActivationStatus.SUSPENDED);
        configs.get(STORE).setStatusReason("Mora en el pago");

        BillingSetupStepDto ready = step(service.getSetup(STORE), Key.READY);

        assertEquals(State.ERROR, ready.state());
        assertTrue(ready.message().contains("Mora en el pago"));
        verify(onboarding, never()).readEnablement(any(), any());
    }

    // --- PUT /billing/setup/fiscal-profile ---

    @Test
    void savingTheFiscalProfileCreatesTheCompanyInProductionOutsideTheTransaction() {
        when(onboarding.createCompany(eq(PROD), any())).thenReturn("co-77");

        BillingSetupDto dto = service.saveFiscalProfile(STORE, request(), ADMIN);

        BillingCompanyProfile saved = profiles.get(STORE);
        assertEquals("Tienda La Esquina SAS", saved.getLegalName());
        assertEquals("La Esquina", saved.getTradeName());
        assertEquals("900123456", saved.getNit());
        assertEquals("8", saved.getDv());
        assertEquals(1, saved.getOrganizationType());
        assertEquals("O-13;O-47", saved.getRegimeCode());
        assertEquals("4711", saved.getEconomicActivity());
        assertEquals("contabilidad@esquina.co", saved.getEmail());
        assertEquals("05001", saved.getCityCode());
        assertEquals("05", saved.getDepartmentCode());

        ArgumentCaptor<CompanyProfileData> data = ArgumentCaptor.forClass(CompanyProfileData.class);
        InOrder order = inOrder(transactionManager, configService, onboarding);
        order.verify(configService).startSelfServiceSetup(STORE, ADMIN);
        order.verify(transactionManager).commit(any());
        order.verify(onboarding).createCompany(eq(PROD), data.capture());
        order.verify(configService).updateProviderCompanyId(STORE, PROD, "co-77", ADMIN);

        assertEquals("Tienda La Esquina SAS", data.getValue().name());
        assertEquals("La Esquina", data.getValue().tradeName());
        assertEquals("900123456", data.getValue().identification());
        assertEquals("8", data.getValue().dv());
        assertEquals("31", data.getValue().identificationType());
        assertEquals(List.of("4711"), data.getValue().economicActivities());
        assertEquals("05001", data.getValue().cityCode());
        assertEquals("05", data.getValue().departmentCode());

        assertEquals(PROD, configs.get(STORE).getEnvironment());
        assertEquals(BillingActivationStatus.IN_SETUP, dto.status());
        assertEquals(State.DONE, step(dto, Key.FISCAL_DATA).state());
        assertEquals(State.CURRENT, step(dto, Key.DIAN_ENABLEMENT).state());
        assertTrue(dto.profile().saved());
    }

    @Test
    void savingAgainUpdatesTheExistingCompanyInsteadOfCreatingAnother() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);

        service.saveFiscalProfile(STORE, request(), ADMIN);

        verify(onboarding).updateCompany(eq(PROD), eq("co-1"), any());
        verify(onboarding, never()).createCompany(any(), any());
        verify(configService, never()).updateProviderCompanyId(any(), any(), any(), any());
    }

    @Test
    void aReceivedCompanyIsReattachedAfterLocalAssociationFailsWithoutCreatingAgain() {
        when(onboarding.createCompany(eq(PROD), any())).thenReturn("co-recovered");
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(inv -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("Local association unavailable");
            }
            configs.get(STORE).assignCompanyId(PROD, inv.getArgument(2));
            return null;
        }).when(configService).updateProviderCompanyId(eq(STORE), eq(PROD), anyString(), eq(ADMIN));

        BillingSetupDto failed = assertDoesNotThrow(() -> service.saveFiscalProfile(STORE, request(), ADMIN));
        assertEquals(State.ERROR, step(failed, Key.FISCAL_DATA).state());
        assertNotNull(profiles.get(STORE));

        BillingSetupDto recovered = service.saveFiscalProfile(STORE, request(), ADMIN);

        assertEquals(State.DONE, step(recovered, Key.FISCAL_DATA).state());
        assertEquals("co-recovered", configs.get(STORE).companyIdFor(PROD));
        verify(onboarding, times(1)).createCompany(eq(PROD), any());
    }

    @Test
    void anUncertainCreationIsNotBlindlyPostedAgain() {
        when(onboarding.createCompany(eq(PROD), any()))
                .thenThrow(new AlegraApiException(0, "Provider timed out after accepting the request", null));

        service.saveFiscalProfile(STORE, request(), ADMIN);
        BillingSetupDto retried = service.saveFiscalProfile(STORE, request(), ADMIN);

        assertEquals(State.ERROR, step(retried, Key.FISCAL_DATA).state());
        assertNull(configs.get(STORE).companyIdFor(PROD));
        verify(onboarding, times(1)).createCompany(eq(PROD), any());
        BillingSetupStepDto afterReload = step(service.getSetup(STORE), Key.FISCAL_DATA);
        assertEquals(State.ERROR, afterReload.state());
        assertTrue(afterReload.message().contains("soporte"));
    }

    @Test
    void aConfirmedValidationRejectionCanBeCorrectedWithoutManualAssociation() {
        when(onboarding.createCompany(eq(PROD), any()))
                .thenThrow(new AlegraApiException(422, "Invalid fiscal data", null))
                .thenReturn("co-corrected");

        assertEquals(State.ERROR, step(service.saveFiscalProfile(STORE, request(), ADMIN), Key.FISCAL_DATA).state());
        BillingSetupDto corrected = service.saveFiscalProfile(STORE, request(), ADMIN);

        assertEquals(State.DONE, step(corrected, Key.FISCAL_DATA).state());
        assertEquals("co-corrected", configs.get(STORE).companyIdFor(PROD));
        verify(onboarding, times(2)).createCompany(eq(PROD), any());
    }

    @Test
    void theMockWizardCompletesWithoutProductionCredentials() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        MockOnboardingProvider local = new MockOnboardingProvider(clock);
        when(onboarding.createCompany(any(), any())).thenAnswer(inv -> local.createCompany(inv.getArgument(0), inv.getArgument(1)));
        when(onboarding.readEnablement(any(), any())).thenAnswer(inv -> local.readEnablement(inv.getArgument(0), inv.getArgument(1)));
        when(onboarding.listResolutions(any(), any())).thenAnswer(inv -> local.listResolutions(inv.getArgument(0), inv.getArgument(1)));
        List<NumberingRangeDto> imported = new ArrayList<>();
        when(rangeService.listNumberingRanges()).thenAnswer(inv -> List.copyOf(imported));
        when(rangeService.createNumberingRange(any())).thenAnswer(inv -> {
            CreateNumberingRangeRequest request = inv.getArgument(0);
            NumberingRangeDto created = range(request.document(), request.prefix(), request.resolutionNumber(), true);
            imported.add(created);
            return created;
        });
        when(provider.hasUsableInvoiceRange(STORE, PROD)).thenAnswer(inv -> imported.stream()
                .anyMatch(range -> "01".equals(range.document())));
        when(provider.countInvoiceRanges(STORE, PROD)).thenAnswer(inv -> (int) imported.stream()
                .filter(range -> "01".equals(range.document())).count());

        BillingSetupDto fiscal = service.saveFiscalProfile(STORE, request(), ADMIN);
        assertEquals(State.DONE, step(fiscal, Key.FISCAL_DATA).state());
        service.startDianEnablement(STORE, TEST_SET, ADMIN);
        NumberingImportResultDto result = service.importNumbering(STORE, ADMIN);

        assertEquals(1, result.importedCount());
        assertEquals(2, result.noteRangesCreated());
        assertEquals(BillingActivationStatus.ACTIVE, result.setup().status());
        assertTrue(result.setup().steps().stream().allMatch(step -> step.state() == State.DONE));
        assertEquals(PROD, result.setup().environment());
    }

    @Test
    void withoutThePlatformTokenTheDataIsKeptAndTheStoreIsTold() {
        when(onboarding.isPlatformReady(PROD)).thenReturn(false);

        BillingSetupDto dto = service.saveFiscalProfile(STORE, request(), ADMIN);

        assertNotNull(profiles.get(STORE), "los datos quedan guardados");
        verify(onboarding, never()).createCompany(any(), any());
        assertFalse(dto.platformReady());
        assertEquals(State.ERROR, step(dto, Key.FISCAL_DATA).state());
        assertEquals(PLATFORM_NOT_READY, step(dto, Key.FISCAL_DATA).message());
        // Y la siguiente consulta lo sigue diciendo.
        assertEquals(PLATFORM_NOT_READY, step(service.getSetup(STORE), Key.FISCAL_DATA).message());
    }

    @Test
    void aProviderRejectionIsShownAsAClearFiscalDataError() {
        when(onboarding.createCompany(eq(PROD), any())).thenThrow(new AlegraApiException(400,
                "No se pudo crear la empresa en Alegra (Alegra HTTP 400): el NIT ya existe", null));

        BillingSetupDto dto = service.saveFiscalProfile(STORE, request(), ADMIN);

        BillingSetupStepDto fiscal = step(dto, Key.FISCAL_DATA);
        assertEquals(State.ERROR, fiscal.state());
        assertEquals("No se pudo crear la empresa en Alegra (Alegra HTTP 400): el NIT ya existe", fiscal.message());
        verify(configService, never()).updateProviderCompanyId(any(), any(), any(), any());
    }

    @Test
    void anUnexpectedFailureNeverLeaksItsTechnicalMessage() {
        when(onboarding.createCompany(eq(PROD), any())).thenThrow(new NullPointerException("company.body is null"));

        BillingSetupStepDto fiscal = step(service.saveFiscalProfile(STORE, request(), ADMIN), Key.FISCAL_DATA);

        assertEquals(State.ERROR, fiscal.state());
        assertFalse(fiscal.message().contains("null"), fiscal.message());
        assertFalse(fiscal.message().contains("Error en proveedor"), fiscal.message());
    }

    // --- POST /billing/setup/dian-enablement ---

    @Test
    void theTestSetNeedsTheFiscalDataFirst() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.startDianEnablement(STORE, TEST_SET, ADMIN));

        assertTrue(ex.getMessage().contains("datos fiscales"), ex.getMessage());
        verifyNoInteractions(onboarding);
    }

    @Test
    void theTestSetGoesToTheProductionCompanyAndTheStepShowsTheDianAnswer() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("PENDING");

        BillingSetupDto dto = service.startDianEnablement(STORE, " " + TEST_SET + " ", ADMIN);

        InOrder order = inOrder(onboarding);
        order.verify(onboarding).startDianEnablement(PROD, "co-1", TEST_SET);
        order.verify(onboarding).readEnablement(PROD, "co-1");
        assertEquals(State.CURRENT, step(dto, Key.DIAN_ENABLEMENT).state());
    }

    @Test
    void anAuthorizedTestSetWithARangeActivatesTheStore() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        when(provider.hasUsableInvoiceRange(STORE, PROD)).thenReturn(true);

        BillingSetupDto dto = service.startDianEnablement(STORE, TEST_SET, ADMIN);

        verify(configService).transition(STORE, BillingActivationStatus.ACTIVE, null, "system");
        assertEquals(BillingActivationStatus.ACTIVE, dto.status());
    }

    @Test
    void aRejectedTestSetIsShownOnTheDianStep() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        doThrow(new AlegraApiException(400,
                "No se pudo iniciar la habilitación ante la DIAN en Alegra (Alegra HTTP 400): set de pruebas inválido", null))
                .when(onboarding).startDianEnablement(any(), any(), any());

        BillingSetupStepDto dian = step(service.startDianEnablement(STORE, TEST_SET, ADMIN), Key.DIAN_ENABLEMENT);

        assertEquals(State.ERROR, dian.state());
        assertTrue(dian.message().contains("set de pruebas inválido"), dian.message());
    }

    // --- POST /billing/setup/numbering/import ---

    @Test
    void numberingCannotBeImportedBeforeTheDianAuthorizesTheCompany() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("PENDING");

        assertThrows(IllegalStateException.class, () -> service.importNumbering(STORE, ADMIN));
        verify(onboarding, never()).listResolutions(any(), any());
        verify(rangeService, never()).createNumberingRange(any());
    }

    @Test
    void numberingIsOnlyImportedForTheStoreInContext() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        TenantContext.setStoreId(STORE + 1);

        assertThrows(IllegalStateException.class, () -> service.importNumbering(STORE, ADMIN));
        verify(onboarding, never()).listResolutions(any(), any());
    }

    @Test
    void numberingIsOnlyImportedInProduction() {
        storeWithCompany(BillingActivationStatus.ACTIVE);
        configs.get(STORE).setEnvironment(BillingEnvironment.SANDBOX);

        assertThrows(IllegalStateException.class, () -> service.importNumbering(STORE, ADMIN));
        verify(rangeService, never()).createNumberingRange(any());
    }

    @Test
    void importRegistersTheDianResolutionAndTheNoteRangesThenActivates() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        when(onboarding.listResolutions(PROD, "900123456")).thenReturn(List.of(
                resolution("FE", "18764000000001", TODAY.minusMonths(1), TODAY.plusYears(2))));
        List<NumberingRangeDto> registered = new ArrayList<>();
        when(rangeService.listNumberingRanges()).thenAnswer(inv -> List.copyOf(registered));
        when(rangeService.createNumberingRange(any())).thenAnswer(inv -> {
            CreateNumberingRangeRequest r = inv.getArgument(0);
            NumberingRangeDto created = range(r.document(), r.prefix(), r.resolutionNumber(), true);
            registered.add(created);
            return created;
        });
        when(provider.hasUsableInvoiceRange(STORE, PROD)).thenAnswer(inv -> !registered.isEmpty());

        NumberingImportResultDto result = service.importNumbering(STORE, ADMIN);

        ArgumentCaptor<CreateNumberingRangeRequest> created = ArgumentCaptor.forClass(CreateNumberingRangeRequest.class);
        verify(rangeService, times(3)).createNumberingRange(created.capture());
        assertEquals(new CreateNumberingRangeRequest("01", "FE", "18764000000001", 1L, 1L, 5000L,
                TODAY.minusMonths(1).toString(), TODAY.plusYears(2).toString(), "tk-18764000000001"),
                created.getAllValues().get(0));
        assertEquals(new CreateNumberingRangeRequest("91", "NC", "SIN-RESOLUCION", 1L, 1L, 99_999_999L,
                TODAY.toString(), TODAY.plusYears(10).toString(), null), created.getAllValues().get(1));
        assertEquals(new CreateNumberingRangeRequest("92", "ND", "SIN-RESOLUCION", 1L, 1L, 99_999_999L,
                TODAY.toString(), TODAY.plusYears(10).toString(), null), created.getAllValues().get(2));

        assertEquals(1, result.importedCount());
        assertEquals(2, result.noteRangesCreated());
        assertTrue(result.skipped().isEmpty(), result.skipped().toString());
        verify(configService).transition(STORE, BillingActivationStatus.ACTIVE, null, "system");
        assertEquals(BillingActivationStatus.ACTIVE, result.setup().status());
    }

    @Test
    void importSkipsRegisteredAndExpiredResolutionsAndKeepsExistingNoteRanges() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        when(onboarding.listResolutions(PROD, "900123456")).thenReturn(List.of(
                resolution("FE", "18764000000001", TODAY.minusYears(1), TODAY.plusYears(1)),
                resolution("FV", "18760000000009", TODAY.minusYears(3), TODAY.minusDays(1))));
        when(rangeService.listNumberingRanges()).thenReturn(List.of(
                range("01", "FE", "18764000000001", true),
                range("91", "NC", "SIN-RESOLUCION", true),
                range("92", "ND", "SIN-RESOLUCION", true)));

        NumberingImportResultDto result = service.importNumbering(STORE, ADMIN);

        verify(rangeService, never()).createNumberingRange(any());
        assertEquals(0, result.importedCount());
        assertEquals(0, result.noteRangesCreated());
        assertEquals(1, result.skipped().size(), result.skipped().toString());
        assertTrue(result.skipped().get(0).contains("18760000000009"));
    }

    @Test
    void aResolutionTheRangeGuardsRefuseIsReportedNotThrown() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        when(onboarding.listResolutions(PROD, "900123456")).thenReturn(List.of(
                resolution("FE2", "18764000000002", TODAY.minusDays(1), TODAY.plusYears(1))));
        when(rangeService.listNumberingRanges()).thenReturn(List.of(range("01", "FE", "18764000000001", true)));
        when(rangeService.createNumberingRange(argThat(r -> r != null && "01".equals(r.document()))))
                .thenThrow(new IllegalStateException(
                        "Ya tienes un rango activo para el documento '01'. Desactívalo antes de activar otro."));

        NumberingImportResultDto result = service.importNumbering(STORE, ADMIN);

        assertEquals(0, result.importedCount());
        assertTrue(result.skipped().stream().anyMatch(s -> s.contains("Ya tienes un rango activo")),
                result.skipped().toString());
    }

    @Test
    void noteRangesAreNotCreatedWhileThereIsNoInvoiceRange() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        when(onboarding.listResolutions(PROD, "900123456")).thenReturn(List.of());

        NumberingImportResultDto result = service.importNumbering(STORE, ADMIN);

        verify(rangeService, never()).createNumberingRange(any());
        BillingSetupStepDto numbering = step(result.setup(), Key.NUMBERING);
        assertEquals(State.ERROR, numbering.state());
        assertTrue(numbering.message().contains("900123456"), numbering.message());
    }

    @Test
    void aDianLookupFailureIsShownOnTheNumberingStep() {
        storeWithCompany(BillingActivationStatus.IN_SETUP);
        dianSays("AUTHORIZED");
        when(onboarding.listResolutions(PROD, "900123456"))
                .thenThrow(new BillingNotEnabledException("La DIAN todavía no habilita a esta empresa con Alegra"));

        NumberingImportResultDto result = service.importNumbering(STORE, ADMIN);

        assertEquals(0, result.importedCount());
        BillingSetupStepDto numbering = step(result.setup(), Key.NUMBERING);
        assertEquals(State.ERROR, numbering.state());
        assertEquals("La DIAN todavía no habilita a esta empresa con Alegra", numbering.message());
    }
}
