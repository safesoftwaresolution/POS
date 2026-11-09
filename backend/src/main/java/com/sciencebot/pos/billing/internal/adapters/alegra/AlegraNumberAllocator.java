package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.entities.DocumentNumberAllocation;
import com.sciencebot.pos.billing.internal.entities.NumberingRange;
import com.sciencebot.pos.billing.internal.repositories.DocumentNumberAllocationRepository;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asigna el consecutivo y la resolución que Alegra exige en cada documento: el sistema envía el
 * {@code number}, Alegra no lo asigna. Cada asignación corre en su propia transacción y toma el lock
 * pesimista de la fila del rango activo, así que dos emisiones simultáneas nunca reciben el mismo número ni
 * dejan huecos. Es idempotente por {@code (local, entorno, documento, referencia)}: reintentar una emisión
 * devuelve el mismo número y no consume otro.
 * <p>
 * Entre los rangos activos del documento usa primero el que el local eligió como su rango del entorno
 * ({@code store_billing_config.sandbox/production_numbering_range_id}) si está vigente y con cupo; si no, el primero
 * utilizable. El lock se toma sobre todos los rangos activos del documento, así que el elegido también queda bloqueado.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraNumberAllocator {

    /** Número asignado a una referencia, con los datos de resolución que exige el documento. */
    public record Allocation(Long id, Long rangeId, String prefix, long number, String resolutionNumber,
                             long minNumber, long maxNumber, LocalDate startDate, LocalDate endDate, String technicalKey,
                             String idempotencyKey, boolean completed, String outcome, String providerDocumentId,
                             String requestPayload, String responsePayload) {
        public Allocation(Long id, Long rangeId, String prefix, long number, String resolutionNumber,
                          long minNumber, long maxNumber, LocalDate startDate, LocalDate endDate, String technicalKey,
                          String idempotencyKey, boolean completed) {
            this(id, rangeId, prefix, number, resolutionNumber, minNumber, maxNumber, startDate, endDate,
                    technicalKey, idempotencyKey, completed, null, null, null, null);
        }
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final NumberingRangeRepository rangeRepository;
    private final DocumentNumberAllocationRepository allocationRepository;
    private final StoreBillingConfigRepository configRepository;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    @Autowired
    public AlegraNumberAllocator(NumberingRangeRepository rangeRepository,
                                 DocumentNumberAllocationRepository allocationRepository,
                                 StoreBillingConfigRepository configRepository,
                                 PlatformTransactionManager transactionManager) {
        this(rangeRepository, allocationRepository, configRepository, transactionManager, Clock.systemDefaultZone());
    }

    AlegraNumberAllocator(NumberingRangeRepository rangeRepository,
                          DocumentNumberAllocationRepository allocationRepository,
                          StoreBillingConfigRepository configRepository,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.rangeRepository = rangeRepository;
        this.allocationRepository = allocationRepository;
        this.configRepository = configRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * Devuelve el número de la referencia; si no tenía, toma el siguiente del rango activo. Falla con
     * {@link BillingNotEnabledException} (mensaje accionable) si no hay rango activo, está vencido, aún no
     * vigente o agotado.
     */
    public Allocation allocate(Long storeId, BillingEnvironment environment, String document, String referenceCode) {
        Objects.requireNonNull(storeId, "El ID del local es obligatorio");
        Objects.requireNonNull(environment, "El entorno es obligatorio");
        Objects.requireNonNull(document, "El tipo de documento es obligatorio");
        if (referenceCode == null || referenceCode.isBlank()) {
            throw new IllegalArgumentException("La referencia es obligatoria");
        }
        String reference = referenceCode.trim();
        return requiresNew.execute(status -> doAllocate(storeId, environment, document, reference));
    }

    /** Marca que el proveedor ya recibió el documento con el número asignado. */
    public void markCompleted(Long allocationId) {
        requiresNew.executeWithoutResult(status -> {
            DocumentNumberAllocation allocation = loadForUpdate(allocationId);
            allocation.markCompleted();
            allocationRepository.save(allocation);
        });
    }

    /** Genera una llave de idempotencia nueva conservando el número (tras una respuesta final no aceptada). */
    public Allocation rotateIdempotencyKey(Long allocationId) {
        return requiresNew.execute(status -> {
            DocumentNumberAllocation allocation = loadForUpdate(allocationId);
            allocation.rotateIdempotencyKey(UUID.randomUUID().toString());
            return toAllocation(allocationRepository.save(allocation));
        });
    }

    /** Freeze before HTTP: later attempts replay these exact bytes, even if customer/company data changed. */
    public String freezeRequest(Long allocationId, Map<String, Object> body) {
        String payload = JSON.writeValueAsString(body);
        return requiresNew.execute(status -> {
            DocumentNumberAllocation allocation = loadForUpdate(allocationId);
            allocation.freezeRequest(payload, sha256(payload));
            allocationRepository.save(allocation);
            return allocation.getRequestPayload();
        });
    }

    /** A durable receipt is independent of the invoice/note finalization transaction. */
    public void recordResult(Long allocationId, InvoiceResult result) {
        if (allocationId == null || result == null) return;
        String outcome = switch (result.status()) {
            case "VALIDATED" -> "ACCEPTED";
            case "REJECTED" -> "REJECTED";
            case "ERROR" -> result.providerDocumentId() != null ? "FAILED" : "UNCERTAIN";
            default -> "PENDING";
        };
        String payload = JSON.writeValueAsString(result);
        requiresNew.executeWithoutResult(status -> {
            DocumentNumberAllocation allocation = loadForUpdate(allocationId);
            allocation.recordResponse(outcome, result.providerDocumentId(), payload);
            allocationRepository.save(allocation);
        });
    }

    public InvoiceResult receipt(Allocation allocation) {
        return allocation.responsePayload() == null ? null : JSON.readValue(allocation.responsePayload(), InvoiceResult.class);
    }

    public Optional<Allocation> findAllocation(Long storeId, BillingEnvironment environment, String document, String reference) {
        return requiresNew.execute(status -> find(storeId, environment, document, reference).map(this::toAllocation));
    }

    private static String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Allocation doAllocate(Long storeId, BillingEnvironment environment, String document, String reference) {
        Optional<DocumentNumberAllocation> existing = find(storeId, environment, document, reference);
        if (existing.isPresent()) {
            return toAllocation(existing.get());
        }

        List<NumberingRange> ranges = rangeRepository.findActiveForUpdate(storeId, environment, document);

        // Con el lock tomado otra llamada con la misma referencia pudo haber confirmado antes: se revisa de nuevo.
        existing = find(storeId, environment, document, reference);
        if (existing.isPresent()) {
            return toAllocation(existing.get());
        }

        NumberingRange range = usableRange(defaultFirst(ranges, storeId, environment), environment, document);
        long number = range.takeNextNumber();
        rangeRepository.save(range);
        DocumentNumberAllocation saved = allocationRepository.save(new DocumentNumberAllocation(
                storeId, environment, document, reference, range.getId(), range.getPrefix(), number,
                UUID.randomUUID().toString()));
        return toAllocation(saved, range);
    }

    /**
     * Los rangos ya bloqueados, con el rango elegido por el local primero si está entre ellos y se puede usar hoy;
     * si no se puede usar, queda en su lugar y {@link #usableRange} sigue con los demás como antes.
     */
    private List<NumberingRange> defaultFirst(List<NumberingRange> ranges, Long storeId, BillingEnvironment environment) {
        Integer defaultId = configRepository.findById(storeId)
                .map(config -> config.numberingRangeIdFor(environment))
                .orElse(null);
        if (defaultId == null) {
            return ranges;
        }
        LocalDate today = LocalDate.now(clock);
        Optional<NumberingRange> chosen = ranges.stream()
                .filter(range -> range.getId() != null && range.getId().longValue() == defaultId.longValue())
                .filter(range -> range.isUsableOn(today))
                .findFirst();
        if (chosen.isEmpty()) {
            return ranges;
        }
        List<NumberingRange> ordered = new ArrayList<>(ranges.size());
        ordered.add(chosen.get());
        ranges.stream().filter(range -> range != chosen.get()).forEach(ordered::add);
        return ordered;
    }

    private NumberingRange usableRange(List<NumberingRange> ranges, BillingEnvironment environment, String document) {
        if (ranges.isEmpty()) {
            throw new BillingNotEnabledException("No hay un rango de numeración activo para el documento " + document
                    + " en el entorno " + environment + ". Carga la resolución antes de facturar");
        }
        LocalDate today = LocalDate.now(clock);
        BillingNotEnabledException lastProblem = null;
        for (NumberingRange range : ranges) {
            if (range.getEndDate() != null && today.isAfter(range.getEndDate())) {
                lastProblem = new BillingNotEnabledException("La resolución " + range.getResolutionNumber()
                        + " venció el " + range.getEndDate() + ". Carga una resolución vigente");
            } else if (range.getStartDate() != null && today.isBefore(range.getStartDate())) {
                lastProblem = new BillingNotEnabledException("La resolución " + range.getResolutionNumber()
                        + " aún no está vigente (inicia el " + range.getStartDate() + ")");
            } else if (range.getNextNumber() > range.getMaxNumber()) {
                lastProblem = new BillingNotEnabledException("El rango " + range.getPrefix()
                        + " está agotado (último número " + range.getMaxNumber() + "). Solicita una nueva resolución");
            } else {
                return range;
            }
        }
        throw lastProblem;
    }

    private Optional<DocumentNumberAllocation> find(Long storeId, BillingEnvironment environment, String document,
                                                    String reference) {
        return allocationRepository.findByStoreIdAndEnvironmentAndDocumentAndReferenceCode(
                storeId, environment, document, reference);
    }

    private DocumentNumberAllocation load(Long allocationId) {
        return allocationRepository.findById(allocationId).orElseThrow(
                () -> new IllegalArgumentException("No existe la asignación de numeración " + allocationId));
    }

    private DocumentNumberAllocation loadForUpdate(Long allocationId) {
        Long storeId = allocationRepository.findStoreIdById(allocationId).orElseThrow(
                () -> new IllegalArgumentException("No existe la asignación de numeración " + allocationId));
        return allocationRepository.findAllocationForUpdateAndStoreId(allocationId, storeId).orElseThrow(
                () -> new IllegalArgumentException("No existe la asignación de numeración " + allocationId));
    }

    private Allocation toAllocation(DocumentNumberAllocation allocation) {
        NumberingRange range = rangeRepository.findById(allocation.getRangeId()).orElseThrow(
                () -> new IllegalStateException("La asignación " + allocation.getId() + " apunta a un rango inexistente"));
        return toAllocation(allocation, range);
    }

    private static Allocation toAllocation(DocumentNumberAllocation allocation, NumberingRange range) {
        return new Allocation(allocation.getId(), allocation.getRangeId(), allocation.getPrefix(),
                allocation.getNumber(), range.getResolutionNumber(), range.getMinNumber(), range.getMaxNumber(),
                range.getStartDate(), range.getEndDate(),
                range.getTechnicalKey(), allocation.getIdempotencyKey(), allocation.isCompleted(), allocation.getOutcome(),
                allocation.getProviderDocumentId(), allocation.getRequestPayload(), allocation.getResponsePayload());
    }
}
