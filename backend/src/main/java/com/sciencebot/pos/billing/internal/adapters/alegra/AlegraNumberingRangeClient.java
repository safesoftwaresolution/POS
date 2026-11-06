package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.BillingOnboardingProvider;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyProfile;
import com.sciencebot.pos.billing.internal.entities.NumberingRange;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.mappers.DianResolutionMapper;
import com.sciencebot.pos.billing.internal.repositories.BillingCompanyProfileRepository;
import com.sciencebot.pos.billing.internal.repositories.DocumentNumberAllocationRepository;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Rangos de numeración de Alegra sobre las tablas locales: con Alegra el sistema lleva el consecutivo, así que
 * "el proveedor" de rangos es la propia base de datos. Mantiene la semántica de
 * {@link com.sciencebot.pos.billing.internal.adapters.NumberingRangeProvider} (el adaptador de Alegra lo
 * expondrá como ese puerto). Todo se limita al local en contexto y a su entorno activo, y los cambios toman el
 * mismo lock de fila que {@link AlegraNumberAllocator} para no cruzarse con una asignación en curso.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraNumberingRangeClient {

    private static final Map<String, String> DOCUMENT_NAMES = Map.of(
            "01", "Factura de Venta", "91", "Nota Crédito", "92", "Nota Débito");

    private final NumberingRangeRepository rangeRepository;
    private final DocumentNumberAllocationRepository allocationRepository;
    private final StoreBillingConfigRepository configRepository;
    private final BillingCompanyProfileRepository profileRepository;
    private final BillingOnboardingProvider onboardingProvider;
    private final DianResolutionMapper resolutionMapper = new DianResolutionMapper();
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Autowired
    public AlegraNumberingRangeClient(NumberingRangeRepository rangeRepository,
                                      DocumentNumberAllocationRepository allocationRepository,
                                      StoreBillingConfigRepository configRepository,
                                      BillingCompanyProfileRepository profileRepository,
                                      BillingOnboardingProvider onboardingProvider,
                                      PlatformTransactionManager transactionManager) {
        this(rangeRepository, allocationRepository, configRepository, profileRepository, onboardingProvider,
                transactionManager, Clock.systemDefaultZone());
    }

    AlegraNumberingRangeClient(NumberingRangeRepository rangeRepository,
                               DocumentNumberAllocationRepository allocationRepository,
                               StoreBillingConfigRepository configRepository,
                               BillingCompanyProfileRepository profileRepository,
                               BillingOnboardingProvider onboardingProvider,
                               PlatformTransactionManager transactionManager, Clock clock) {
        this.rangeRepository = rangeRepository;
        this.allocationRepository = allocationRepository;
        this.configRepository = configRepository;
        this.profileRepository = profileRepository;
        this.onboardingProvider = onboardingProvider;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Local y entorno sobre los que opera la solicitud en curso. */
    private record Scope(Long storeId, BillingEnvironment environment) {}

    /**
     * Resoluciones de facturación que la DIAN tiene para el NIT del local en contexto ({@code GET /resolutions/{nit}}).
     * Solo existen en producción: en el entorno de pruebas la respuesta es una lista vacía. La consulta al proveedor
     * se hace fuera de la transacción.
     */
    public List<NumberingRangeDto> queryDianNumberingRanges() {
        Scope scope = transaction.execute(status -> scope());
        if (scope.environment() != BillingEnvironment.PRODUCTION) {
            return List.of();
        }
        String nit = profileRepository.findById(scope.storeId())
                .map(BillingCompanyProfile::getNit)
                .orElseThrow(() -> new IllegalStateException("Registra primero los datos fiscales de tu empresa: "
                        + "con su NIT se consultan las resoluciones ante la DIAN"));
        LocalDate today = LocalDate.now(clock);
        return onboardingProvider.listResolutions(BillingEnvironment.PRODUCTION, nit).stream()
                .map(resolution -> resolutionMapper.toDto(resolution, today))
                .toList();
    }

    /** El local tiene un rango de facturas activo, vigente hoy y con números disponibles en ese entorno. */
    public boolean hasUsableInvoiceRange(Long storeId, BillingEnvironment environment) {
        if (storeId == null || environment == null) {
            return false;
        }
        LocalDate today = LocalDate.now(clock);
        return Boolean.TRUE.equals(transaction.execute(status -> invoiceRanges(storeId, environment).stream()
                .anyMatch(range -> range.isUsableOn(today))));
    }

    /** Rangos de facturas (no eliminados) del local en ese entorno. */
    public int countInvoiceRanges(Long storeId, BillingEnvironment environment) {
        if (storeId == null || environment == null) {
            return 0;
        }
        Integer count = transaction.execute(status -> invoiceRanges(storeId, environment).size());
        return count != null ? count : 0;
    }

    private List<NumberingRange> invoiceRanges(Long storeId, BillingEnvironment environment) {
        return rangeRepository.findByStoreIdAndEnvironmentAndDeletedFalseOrderByIdAsc(storeId, environment).stream()
                .filter(range -> DianResolutionMapper.INVOICE_DOCUMENT.equals(range.getDocument()))
                .toList();
    }

    public List<NumberingRangeDto> listNumberingRanges() {
        return transaction.execute(status -> {
            Scope scope = scope();
            return rangeRepository.findByStoreIdAndEnvironmentAndDeletedFalseOrderByIdAsc(
                    scope.storeId(), scope.environment()).stream().map(this::toDto).toList();
        });
    }

    /** El rango del local, o {@code null} si no existe o es de otro local. */
    public NumberingRangeDto getNumberingRange(Long numberingRangeId) {
        return transaction.execute(status -> {
            Scope scope = scope();
            return rangeRepository.findByIdAndStoreIdAndEnvironmentAndDeletedFalse(
                    numberingRangeId, scope.storeId(), scope.environment()).map(this::toDto).orElse(null);
        });
    }

    public NumberingRangeDto createNumberingRange(CreateNumberingRangeRequest request) {
        return transaction.execute(status -> {
            Scope scope = scope();
            NumberingRange range = buildRange(scope, request);
            if (rangeRepository.existsByStoreIdAndEnvironmentAndDocumentAndPrefixAndResolutionNumber(
                    scope.storeId(), scope.environment(), range.getDocument(), range.getPrefix(),
                    range.getResolutionNumber())) {
                throw new IllegalStateException("Ya existe un rango " + range.getPrefix() + " con la resolución "
                        + range.getResolutionNumber() + " para este documento (puede estar eliminado)");
            }
            return toDto(rangeRepository.save(range));
        });
    }

    /** Sin asignaciones se borra; con asignaciones solo se marca como eliminado para conservar el historial. */
    public void deleteNumberingRange(Long numberingRangeId) {
        transaction.executeWithoutResult(status -> {
            NumberingRange range = lockedRange(numberingRangeId);
            if (allocationRepository.existsByStoreIdAndRangeId(range.getStoreId(), range.getId())) {
                range.markDeleted();
                rangeRepository.save(range);
            } else {
                rangeRepository.delete(range);
            }
        });
    }

    public NumberingRangeDto toggleNumberingRangeStatus(Long numberingRangeId) {
        return transaction.execute(status -> {
            NumberingRange range = lockedRange(numberingRangeId);
            range.toggleActive();
            return toDto(rangeRepository.save(range));
        });
    }

    /** Fija el próximo número a usar. Nunca por debajo de un número ya asignado: repetirlo duplicaría el consecutivo. */
    public NumberingRangeDto updateNumberingRangeCurrent(Long numberingRangeId, Long current) {
        return transaction.execute(status -> {
            NumberingRange range = lockedRange(numberingRangeId);
            Long maxAssigned = allocationRepository.findMaxNumberByStoreIdAndRangeId(range.getStoreId(), range.getId());
            if (maxAssigned != null && current <= maxAssigned) {
                throw new IllegalArgumentException("El consecutivo " + current + " ya fue asignado a un documento "
                        + "(el último número usado es " + maxAssigned + ")");
            }
            range.moveNextNumberTo(current);
            return toDto(rangeRepository.save(range));
        });
    }

    private Scope scope() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new BillingNotEnabledException("No hay un local activo para administrar rangos de numeración");
        }
        StoreBillingConfig config = configRepository.findById(storeId).orElseThrow(
                () -> new BillingNotEnabledException("El local no tiene la facturación electrónica habilitada"));
        return new Scope(storeId, config.getEnvironment());
    }

    private NumberingRange lockedRange(Long numberingRangeId) {
        Scope scope = scope();
        return rangeRepository.findForUpdate(numberingRangeId, scope.storeId(), scope.environment())
                .orElseThrow(() -> new EntityNotFoundException(
                        "El rango de numeración " + numberingRangeId + " no existe en tu cuenta de facturación"));
    }

    private NumberingRange buildRange(Scope scope, CreateNumberingRangeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("La solicitud de rango de numeración no puede ser nula");
        }
        if (request.from() == null || request.to() == null) {
            throw new IllegalArgumentException(
                    "Alegra necesita el consecutivo inicial y final de la resolución (desde y hasta)");
        }
        if (!DOCUMENT_NAMES.containsKey(request.document())) {
            throw new IllegalArgumentException("Tipo de documento no válido: " + request.document()
                    + ". Usa 01 (factura), 91 (nota crédito) o 92 (nota débito)");
        }
        if (request.prefix() == null || request.prefix().isBlank() || request.prefix().trim().length() > 4) {
            throw new IllegalArgumentException("El prefijo es obligatorio y de hasta 4 caracteres");
        }
        if (request.resolutionNumber() == null || request.resolutionNumber().isBlank()
                || request.resolutionNumber().trim().length() > 14) {
            throw new IllegalArgumentException("El número de resolución es obligatorio y de hasta 14 caracteres");
        }
        if (request.from() > request.to()) {
            throw new IllegalArgumentException("El consecutivo inicial no puede ser mayor que el final");
        }
        if (request.current() < request.from() || request.current() > request.to()) {
            throw new IllegalArgumentException("El consecutivo actual debe estar entre " + request.from()
                    + " y " + request.to());
        }
        LocalDate start = parseDate(request.startDate(), "inicio de vigencia");
        LocalDate end = parseDate(request.endDate(), "fin de vigencia");
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("El inicio de vigencia no puede ser posterior al fin");
        }
        NumberingRange range = new NumberingRange(scope.storeId(), scope.environment(), request.document(),
                request.prefix().trim(), request.resolutionNumber().trim(), request.from(), request.to(),
                start, end, request.technicalKey());
        range.moveNextNumberTo(request.current());
        return range;
    }

    private static LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("La fecha de " + label + " no es válida (usa AAAA-MM-DD): " + value);
        }
    }

    private NumberingRangeDto toDto(NumberingRange range) {
        boolean expired = range.getEndDate() != null && LocalDate.now(clock).isAfter(range.getEndDate());
        return new NumberingRangeDto(range.getId(), range.getDocument(), range.getPrefix(),
                range.getResolutionNumber(), range.getMinNumber(), range.getMaxNumber(), range.getNextNumber(),
                range.getStartDate() != null ? range.getStartDate().toString() : null,
                range.getEndDate() != null ? range.getEndDate().toString() : null,
                range.getTechnicalKey(), range.isActive(), DOCUMENT_NAMES.get(range.getDocument()), expired);
    }
}
