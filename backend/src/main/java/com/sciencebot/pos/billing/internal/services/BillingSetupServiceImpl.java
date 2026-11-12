package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.BillingSetupDto;
import com.sciencebot.pos.billing.dto.BillingSetupStepDto;
import com.sciencebot.pos.billing.dto.BillingSetupStepDto.Key;
import com.sciencebot.pos.billing.dto.BillingSetupStepDto.State;
import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.FiscalProfileDto;
import com.sciencebot.pos.billing.dto.FiscalProfileRequest;
import com.sciencebot.pos.billing.dto.NumberingImportResultDto;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.exception.BillingProviderException;
import com.sciencebot.pos.billing.internal.adapters.ActiveBillingProvider;
import com.sciencebot.pos.billing.internal.adapters.BillingOnboardingProvider;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.CompanyProfileData;
import com.sciencebot.pos.billing.internal.adapters.dto.DianEnablementStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.DianResolutionData;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyProfile;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.BillingSetupMapper;
import com.sciencebot.pos.billing.internal.mappers.DianResolutionMapper;
import com.sciencebot.pos.billing.internal.repositories.BillingCompanyProfileRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.stores.StoreFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Asistente de habilitación en autoservicio. Cada escritura sigue los tres pasos de la facturación: guardar en una
 * transacción corta, llamar al proveedor sin transacción y registrar el resultado en otra transacción corta (los
 * métodos de {@link StoreBillingConfigService} y {@link NumberingRangeService} abren la suya). Los locales se
 * habilitan en producción; el sandbox es un recorrido QA separado.
 */
@Service
public class BillingSetupServiceImpl implements BillingSetupService {

    private static final Logger log = LoggerFactory.getLogger(BillingSetupServiceImpl.class);

    static final String SYSTEM_ACTOR = "system";
    static final String PLATFORM_NOT_READY =
            "La facturación electrónica aún no está disponible en la plataforma. Guardamos tus datos y te avisaremos.";
    private static final BillingEnvironment PRODUCTION = BillingEnvironment.PRODUCTION;

    /** Rangos de notas: no llevan resolución de la DIAN (spec «Alta de una empresa en Alegra», paso 6). */
    private static final Map<String, String> NOTE_PREFIXES = new LinkedHashMap<>();
    private static final Map<String, String> NOTE_NAMES = Map.of("91", "notas crédito", "92", "notas débito");
    private static final String NOTE_RESOLUTION = "SIN-RESOLUCION";
    private static final long NOTE_LAST_NUMBER = 99_999_999L;
    private static final int NOTE_VALIDITY_YEARS = 10;

    static {
        NOTE_PREFIXES.put("91", "NC");
        NOTE_PREFIXES.put("92", "ND");
    }

    private final StoreBillingConfigService configService;
    private final StoreBillingConfigRepository configRepository;
    private final BillingCompanyProfileRepository profileRepository;
    private final BillingOnboardingProvider onboardingProvider;
    private final ActiveBillingProvider activeBillingProvider;
    private final NumberingRangeService numberingRangeService;
    private final StoreFacade storeFacade;
    private final BillingSetupMapper mapper;
    private final DianResolutionMapper resolutionMapper;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final BillingCompanyRegistrationTransactions registrations;

    @Autowired
    public BillingSetupServiceImpl(StoreBillingConfigService configService,
                                   StoreBillingConfigRepository configRepository,
                                   BillingCompanyProfileRepository profileRepository,
                                   BillingOnboardingProvider onboardingProvider,
                                   ActiveBillingProvider activeBillingProvider,
                                   NumberingRangeService numberingRangeService,
                                   @Lazy StoreFacade storeFacade,
                                   BillingSetupMapper mapper,
                                   DianResolutionMapper resolutionMapper,
                                   PlatformTransactionManager transactionManager,
                                   BillingCompanyRegistrationTransactions registrations) {
        this(configService, configRepository, profileRepository, onboardingProvider, activeBillingProvider,
                numberingRangeService, storeFacade, mapper, resolutionMapper, transactionManager,
                Clock.systemDefaultZone(), registrations);
    }

    BillingSetupServiceImpl(StoreBillingConfigService configService,
                            StoreBillingConfigRepository configRepository,
                            BillingCompanyProfileRepository profileRepository,
                            BillingOnboardingProvider onboardingProvider,
                            ActiveBillingProvider activeBillingProvider,
                            NumberingRangeService numberingRangeService,
                            StoreFacade storeFacade,
                            BillingSetupMapper mapper,
                            DianResolutionMapper resolutionMapper,
                            PlatformTransactionManager transactionManager,
                            Clock clock,
                            BillingCompanyRegistrationTransactions registrations) {
        this.configService = configService;
        this.configRepository = configRepository;
        this.profileRepository = profileRepository;
        this.onboardingProvider = onboardingProvider;
        this.activeBillingProvider = activeBillingProvider;
        this.numberingRangeService = numberingRangeService;
        this.storeFacade = storeFacade;
        this.mapper = mapper;
        this.resolutionMapper = resolutionMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.registrations = registrations;
    }

    /** Lo guardado del local: su fila de facturación (puede no existir) y sus datos fiscales (pueden no existir). */
    private record Snapshot(StoreBillingConfig config, BillingCompanyProfile profile) {

        BillingActivationStatus status() {
            return config != null ? config.getStatus() : BillingActivationStatus.NOT_REQUESTED;
        }

        BillingEnvironment environment() {
            return config != null ? config.getEnvironment() : BillingEnvironment.SANDBOX;
        }

        String companyId() {
            return config != null ? config.companyIdFor(PRODUCTION) : null;
        }

        boolean fiscalDone() {
            return profile != null && companyId() != null;
        }

        boolean activatedBefore() {
            return status() == BillingActivationStatus.ACTIVE || status() == BillingActivationStatus.SUSPENDED;
        }
    }

    private Snapshot snapshot(Long storeId) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        return new Snapshot(configRepository.findById(storeId).orElse(null),
                profileRepository.findById(storeId).orElse(null));
    }

    @Override
    public BillingSetupDto getSetup(Long storeId) {
        return evaluate(storeId, Map.of());
    }

    @Override
    public BillingSetupDto saveFiscalProfile(Long storeId, FiscalProfileRequest request, String actor) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        Objects.requireNonNull(request, "Los datos fiscales son obligatorios");

        // 1. Transacción corta: datos fiscales, fila del local en configuración y en producción.
        transaction.executeWithoutResult(status -> {
            BillingCompanyProfile profile = profileRepository.findById(storeId).orElseGet(() -> {
                BillingCompanyProfile created = new BillingCompanyProfile();
                created.setStoreId(storeId);
                return created;
            });
            mapper.apply(request, profile);
            profileRepository.save(profile);
            configService.startSelfServiceSetup(storeId, actor);
        });

        if (!onboardingProvider.isPlatformReady(PRODUCTION)) {
            return evaluate(storeId, Map.of(Key.FISCAL_DATA, PLATFORM_NOT_READY));
        }

        // 2. Sin transacción: crear o actualizar la empresa en el proveedor.
        Snapshot snapshot = snapshot(storeId);
        CompanyProfileData data = mapper.toCompanyData(snapshot.profile());
        try {
            if (snapshot.companyId() == null) {
                registerCompany(storeId, data, actor);
            } else {
                onboardingProvider.updateCompany(PRODUCTION, snapshot.companyId(), data);
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar la empresa del local {} en el proveedor: {}", storeId, ex.toString());
            return evaluate(storeId, Map.of(Key.FISCAL_DATA, describe(ex,
                    "No pudimos registrar tu empresa ante el proveedor de facturación. Intenta de nuevo en unos minutos.")));
        }

        return evaluate(storeId, Map.of());
    }

    private void registerCompany(Long storeId, CompanyProfileData data, String actor) {
        BillingCompanyRegistrationTransactions.Reservation reservation = registrations.reserve(
                storeId, PRODUCTION, activeBillingProvider.configuredName(), data.identification());
        String companyId = reservation.providerCompanyId();
        if (!reservation.hasReceipt()) {
            try {
                companyId = onboardingProvider.createCompany(PRODUCTION, data);
            } catch (RuntimeException ex) {
                // Only explicit validation rejection proves that no company was created. Timeouts remain uncertain.
                boolean rejected = ex instanceof BillingProviderException providerException
                        && (providerException.getUpstreamStatus() == 400 || providerException.getUpstreamStatus() == 422);
                registrations.recordFailure(reservation, rejected);
                throw ex;
            }
            registrations.recordReceipt(reservation, companyId);
        }
        // Receipt commits separately BEFORE attachment. A failed local write never triggers another external CREATE.
        configService.updateProviderCompanyId(storeId, PRODUCTION, companyId, actor);
        registrations.markAttached(reservation);
        if (reservation.hasReceipt()) {
            onboardingProvider.updateCompany(PRODUCTION, companyId, data);
        }
    }

    @Override
    public BillingSetupDto startDianEnablement(Long storeId, String testSetId, String actor) {
        Objects.requireNonNull(testSetId, "El código del set de pruebas es obligatorio");
        Snapshot snapshot = snapshot(storeId);
        if (!snapshot.fiscalDone()) {
            throw new IllegalStateException(
                    "Primero registra los datos fiscales de tu empresa: la habilitación ante la DIAN se hace con ella.");
        }
        Map<Key, String> errors = new EnumMap<>(Key.class);
        try {
            onboardingProvider.startDianEnablement(PRODUCTION, snapshot.companyId(), testSetId.trim());
        } catch (RuntimeException ex) {
            log.warn("No se pudo iniciar la habilitación DIAN del local {}: {}", storeId, ex.toString());
            errors.put(Key.DIAN_ENABLEMENT, describe(ex,
                    "No pudimos enviar el set de pruebas a la DIAN. Intenta de nuevo en unos minutos."));
        }
        return evaluate(storeId, errors);
    }

    @Override
    public NumberingImportResultDto importNumbering(Long storeId, String actor) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        // Los rangos se registran para el local de la solicitud: nunca para otro.
        if (!storeId.equals(TenantContext.getStoreId())) {
            throw new IllegalStateException("La numeración solo se puede importar para el local de la sesión");
        }
        Snapshot snapshot = snapshot(storeId);
        if (!snapshot.fiscalDone()) {
            throw new IllegalStateException("Primero registra los datos fiscales de tu empresa.");
        }
        if (snapshot.environment() != PRODUCTION) {
            throw new IllegalStateException("Tu local factura en el entorno de pruebas: la numeración de la DIAN "
                    + "solo se importa en producción.");
        }
        requireDianAuthorization(snapshot);

        List<DianResolutionData> resolutions;
        try {
            resolutions = onboardingProvider.listResolutions(PRODUCTION, snapshot.profile().getNit());
        } catch (RuntimeException ex) {
            log.warn("No se pudieron consultar las resoluciones DIAN del local {}: {}", storeId, ex.toString());
            return new NumberingImportResultDto(0, 0, List.of(), evaluate(storeId, Map.of(Key.NUMBERING, describe(ex,
                    "No pudimos consultar tus resoluciones ante la DIAN. Intenta de nuevo en unos minutos."))));
        }

        LocalDate today = LocalDate.now(clock);
        List<String> skipped = new ArrayList<>();
        List<NumberingRangeDto> registered = numberingRangeService.listNumberingRanges();
        int imported = 0;
        for (DianResolutionData resolution : importOrder(resolutions, today)) {
            if (isRegistered(registered, resolution)) {
                continue;
            }
            if (resolution.endDate() != null && today.isAfter(resolution.endDate())) {
                skipped.add("La resolución " + resolution.resolutionNumber() + " venció el " + resolution.endDate() + ".");
                continue;
            }
            try {
                numberingRangeService.createNumberingRange(resolutionMapper.toInvoiceRange(resolution));
                imported++;
            } catch (IllegalStateException | IllegalArgumentException ex) {
                // Las guardas de rangos (RN-BILL-008) deciden: un solo rango activo por documento, sin duplicados.
                skipped.add("La resolución " + resolution.resolutionNumber() + " no se importó: " + ex.getMessage());
            }
        }

        List<NumberingRangeDto> current = imported > 0 ? numberingRangeService.listNumberingRanges() : registered;
        int notes = createMissingNoteRanges(current, today, skipped);

        Map<Key, String> errors = new EnumMap<>(Key.class);
        if (resolutions.isEmpty() && !activeBillingProvider.get().hasUsableInvoiceRange(storeId, PRODUCTION)) {
            errors.put(Key.NUMBERING, "La DIAN no tiene resoluciones de facturación para el NIT "
                    + snapshot.profile().getNit() + ". Solicita tu numeración en el portal de la DIAN y vuelve a importarla.");
        }
        return new NumberingImportResultDto(imported, notes, List.copyOf(skipped), evaluate(storeId, errors));
    }

    /** Un local en configuración necesita la DIAN en AUTHORIZED; uno que ya estuvo activo no se vuelve a consultar. */
    private void requireDianAuthorization(Snapshot snapshot) {
        if (snapshot.activatedBefore()) {
            return;
        }
        DianEnablementStatus enablement;
        try {
            enablement = onboardingProvider.readEnablement(PRODUCTION, snapshot.companyId());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("No pudimos confirmar tu habilitación ante la DIAN: "
                    + describe(ex, "intenta de nuevo en unos minutos."), ex);
        }
        if (enablement == null || !enablement.isAuthorized()) {
            throw new IllegalStateException("La DIAN aún no te habilita como facturador electrónico. "
                    + "Completa ese paso antes de importar la numeración.");
        }
    }

    /** Primero las vigentes hoy y, entre ellas, las que vencen más tarde: la primera que se registra queda activa. */
    private static List<DianResolutionData> importOrder(List<DianResolutionData> resolutions, LocalDate today) {
        Comparator<DianResolutionData> validToday = Comparator.comparing(
                r -> r.startDate() != null && today.isBefore(r.startDate()));
        Comparator<DianResolutionData> latestEnd = Comparator.comparing(DianResolutionData::endDate,
                Comparator.nullsFirst(Comparator.<LocalDate>reverseOrder()));
        return resolutions.stream().filter(Objects::nonNull).sorted(validToday.thenComparing(latestEnd)).toList();
    }

    private static boolean isRegistered(List<NumberingRangeDto> registered, DianResolutionData resolution) {
        return registered.stream().anyMatch(range -> DianResolutionMapper.INVOICE_DOCUMENT.equals(range.document())
                && Objects.equals(range.prefix(), resolution.prefix())
                && Objects.equals(range.resolutionNumber(), resolution.resolutionNumber()));
    }

    /**
     * Rangos de notas crédito y débito: solo cuando ya hay un rango de facturas activo (así el primero que se
     * registra, el que queda elegido para facturar, siempre es de facturas) y solo los que no tengan uno activo.
     */
    private int createMissingNoteRanges(List<NumberingRangeDto> ranges, LocalDate today, List<String> skipped) {
        if (!hasActive(ranges, DianResolutionMapper.INVOICE_DOCUMENT)) {
            return 0;
        }
        int created = 0;
        for (Map.Entry<String, String> note : NOTE_PREFIXES.entrySet()) {
            if (hasActive(ranges, note.getKey())) {
                continue;
            }
            try {
                numberingRangeService.createNumberingRange(new CreateNumberingRangeRequest(note.getKey(),
                        note.getValue(), NOTE_RESOLUTION, 1L, 1L, NOTE_LAST_NUMBER, today.toString(),
                        today.plusYears(NOTE_VALIDITY_YEARS).toString(), null));
                created++;
            } catch (IllegalStateException | IllegalArgumentException ex) {
                skipped.add("No se creó el rango de " + NOTE_NAMES.get(note.getKey()) + ": " + ex.getMessage());
            }
        }
        return created;
    }

    private static boolean hasActive(List<NumberingRangeDto> ranges, String document) {
        return ranges.stream().anyMatch(range -> document.equals(range.document()) && Boolean.TRUE.equals(range.isActive()));
    }

    // --- avance del asistente ---

    /**
     * Calcula cada paso con lo guardado y, solo para un local en configuración con su empresa creada, con lo que
     * responde la DIAN. Activa al local si ya cumple todo. {@code errors} trae lo que falló en la acción que se acaba
     * de hacer y se muestra en su paso.
     */
    private BillingSetupDto evaluate(Long storeId, Map<Key, String> errors) {
        Snapshot snapshot = snapshot(storeId);
        boolean platformReady = onboardingProvider.isPlatformReady(PRODUCTION);
        Map<Key, String> problems = new EnumMap<>(Key.class);
        problems.putAll(errors);

        if (snapshot.profile() != null && !snapshot.fiscalDone() && !platformReady) {
            problems.putIfAbsent(Key.FISCAL_DATA, PLATFORM_NOT_READY);
        }
        if (snapshot.profile() != null && !snapshot.fiscalDone() && platformReady) {
            String recoveryMessage = registrations.recoveryMessage(storeId, PRODUCTION, activeBillingProvider.configuredName());
            if (recoveryMessage != null) {
                problems.putIfAbsent(Key.FISCAL_DATA, recoveryMessage);
            }
        }

        DianEnablementStatus enablement = null;
        if (snapshot.fiscalDone() && snapshot.status() == BillingActivationStatus.IN_SETUP
                && !problems.containsKey(Key.DIAN_ENABLEMENT)) {
            try {
                enablement = onboardingProvider.readEnablement(PRODUCTION, snapshot.companyId());
            } catch (RuntimeException ex) {
                problems.put(Key.DIAN_ENABLEMENT, "No pudimos consultar tu habilitación ante la DIAN: "
                        + describe(ex, "intenta de nuevo en unos minutos."));
            }
        }
        if (enablement != null && enablement.stage() == DianEnablementStatus.Stage.OTHER) {
            problems.putIfAbsent(Key.DIAN_ENABLEMENT, "La DIAN respondió «" + enablement.rawValue()
                    + "» a tu habilitación. Revisa el código del set de pruebas y vuelve a enviarlo.");
        }
        boolean dianDone = (enablement != null && enablement.isAuthorized())
                || (snapshot.fiscalDone() && snapshot.activatedBefore());

        ElectronicInvoicingProvider provider = activeBillingProvider.get();
        boolean numberingDone = provider.hasUsableInvoiceRange(storeId, PRODUCTION);
        int invoiceRanges = provider.countInvoiceRanges(storeId, PRODUCTION);

        BillingActivationStatus status = snapshot.status();
        if (status == BillingActivationStatus.IN_SETUP && dianDone && numberingDone) {
            try {
                configService.transition(storeId, BillingActivationStatus.ACTIVE, null, SYSTEM_ACTOR);
                status = BillingActivationStatus.ACTIVE;
            } catch (IllegalStateException | IllegalArgumentException ex) {
                // Otra solicitud simultánea pudo activar el local primero (la transición toma el lock de la fila):
                // si ya está ACTIVE, el objetivo se cumplió y no es un error del dueño.
                boolean activatedMeanwhile = configRepository.findById(storeId)
                        .map(config -> config.getStatus() == BillingActivationStatus.ACTIVE).orElse(false);
                if (activatedMeanwhile) {
                    status = BillingActivationStatus.ACTIVE;
                } else {
                    problems.putIfAbsent(Key.READY, ex.getMessage());
                }
            }
        }
        if (status == BillingActivationStatus.SUSPENDED) {
            String reason = snapshot.config().getStatusReason();
            problems.putIfAbsent(Key.READY, "Tu facturación electrónica está suspendida"
                    + (reason != null && !reason.isBlank() ? ": " + reason : "") + ". Escríbenos a soporte para reactivarla.");
        }

        Map<Key, Boolean> done = new EnumMap<>(Key.class);
        done.put(Key.FISCAL_DATA, snapshot.fiscalDone());
        done.put(Key.DIAN_ENABLEMENT, dianDone);
        done.put(Key.NUMBERING, numberingDone);
        done.put(Key.READY, status == BillingActivationStatus.ACTIVE);

        List<BillingSetupStepDto> steps = new ArrayList<>();
        boolean currentAssigned = false;
        for (Key key : Key.values()) {
            State state;
            if (problems.containsKey(key)) {
                state = State.ERROR;
                currentAssigned = true;
            } else if (done.get(key)) {
                state = State.DONE;
            } else if (!currentAssigned) {
                state = State.CURRENT;
                currentAssigned = true;
            } else {
                state = State.PENDING;
            }
            String message = state == State.ERROR ? problems.get(key) : message(key, state, snapshot, enablement);
            steps.add(new BillingSetupStepDto(key, state, message));
        }

        return new BillingSetupDto(status, snapshot.environment(), platformReady, List.copyOf(steps),
                profileFor(storeId, snapshot), invoiceRanges);
    }

    private FiscalProfileDto profileFor(Long storeId, Snapshot snapshot) {
        if (snapshot.profile() != null) {
            return mapper.toDto(snapshot.profile());
        }
        try {
            return mapper.suggestion(storeFacade.getById(storeId));
        } catch (RuntimeException ex) {
            log.debug("Sin datos del local {} para sugerir el perfil fiscal: {}", storeId, ex.getMessage());
            return mapper.emptySuggestion();
        }
    }

    private static String message(Key key, State state, Snapshot snapshot, DianEnablementStatus enablement) {
        return switch (key) {
            case FISCAL_DATA -> switch (state) {
                case DONE -> "Tus datos fiscales están registrados ante el proveedor de facturación.";
                default -> snapshot.profile() == null
                        ? "Ingresa los datos de tu RUT para registrar tu empresa."
                        : "Guardamos tus datos fiscales, pero falta registrarlos ante el proveedor de facturación. "
                        + "Guárdalos de nuevo para reintentar.";
            };
            case DIAN_ENABLEMENT -> switch (state) {
                case DONE -> "La DIAN ya te habilitó como facturador electrónico.";
                case CURRENT -> enablement != null && enablement.stage() == DianEnablementStatus.Stage.PENDING
                        ? "La DIAN está revisando tu set de pruebas. Puede tardar unos minutos; vuelve a consultar más tarde."
                        : "En el portal de la DIAN registra a Alegra como tu proveedor tecnológico y escribe aquí el "
                        + "código del set de pruebas (TestSetId) que te entregue.";
                default -> "Disponible cuando registres tus datos fiscales.";
            };
            case NUMBERING -> switch (state) {
                case DONE -> "Tienes una numeración de facturas vigente.";
                case CURRENT -> "Importa la resolución de numeración que la DIAN te autorizó para facturar.";
                default -> "Disponible cuando la DIAN te habilite como facturador electrónico.";
            };
            case READY -> switch (state) {
                case DONE -> "Tu facturación electrónica está activa: ya puedes emitir facturas ante la DIAN.";
                case CURRENT -> "Todo está listo: estamos activando tu facturación electrónica.";
                default -> "Tu facturación electrónica se activa sola cuando completes los pasos anteriores.";
            };
        };
    }

    /**
     * Mensaje para el usuario: el del proveedor o el de nuestras validaciones (ya están en español y sin datos
     * internos); cualquier otro fallo se reemplaza por {@code fallback} para no mostrar detalles técnicos.
     */
    private static String describe(RuntimeException ex, String fallback) {
        boolean readable = ex instanceof BillingProviderException || ex instanceof BillingNotEnabledException
                || ex instanceof IllegalArgumentException || ex instanceof IllegalStateException;
        String message = ex.getMessage();
        return readable && message != null && !message.isBlank() ? message : fallback;
    }
}
