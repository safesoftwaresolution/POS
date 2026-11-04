package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.entities.DocumentNumberAllocation;
import com.sciencebot.pos.billing.internal.entities.NumberingRange;
import com.sciencebot.pos.billing.internal.repositories.DocumentNumberAllocationRepository;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Asigna el consecutivo y la resolución que Alegra exige en cada documento: el sistema envía el
 * {@code number}, Alegra no lo asigna. Cada asignación corre en su propia transacción y toma el lock
 * pesimista de la fila del rango activo, así que dos emisiones simultáneas nunca reciben el mismo número ni
 * dejan huecos. Es idempotente por {@code (local, entorno, documento, referencia)}: reintentar una emisión
 * devuelve el mismo número y no consume otro.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraNumberAllocator {

    /** Número asignado a una referencia, con los datos de resolución que exige el documento. */
    public record Allocation(Long id, Long rangeId, String prefix, long number, String resolutionNumber,
                             long minNumber, long maxNumber, LocalDate startDate, LocalDate endDate, String technicalKey,
                             String idempotencyKey, boolean completed) {}

    private final NumberingRangeRepository rangeRepository;
    private final DocumentNumberAllocationRepository allocationRepository;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    @Autowired
    public AlegraNumberAllocator(NumberingRangeRepository rangeRepository,
                                 DocumentNumberAllocationRepository allocationRepository,
                                 PlatformTransactionManager transactionManager) {
        this(rangeRepository, allocationRepository, transactionManager, Clock.systemDefaultZone());
    }

    AlegraNumberAllocator(NumberingRangeRepository rangeRepository,
                          DocumentNumberAllocationRepository allocationRepository,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.rangeRepository = rangeRepository;
        this.allocationRepository = allocationRepository;
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
            DocumentNumberAllocation allocation = load(allocationId);
            allocation.markCompleted();
            allocationRepository.save(allocation);
        });
    }

    /** Genera una llave de idempotencia nueva conservando el número (tras una respuesta final no aceptada). */
    public Allocation rotateIdempotencyKey(Long allocationId) {
        return requiresNew.execute(status -> {
            DocumentNumberAllocation allocation = load(allocationId);
            allocation.rotateIdempotencyKey(UUID.randomUUID().toString());
            return toAllocation(allocationRepository.save(allocation));
        });
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

        NumberingRange range = usableRange(ranges, environment, document);
        long number = range.takeNextNumber();
        rangeRepository.save(range);
        DocumentNumberAllocation saved = allocationRepository.save(new DocumentNumberAllocation(
                storeId, environment, document, reference, range.getId(), range.getPrefix(), number,
                UUID.randomUUID().toString()));
        return toAllocation(saved, range);
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

    private Allocation toAllocation(DocumentNumberAllocation allocation) {
        NumberingRange range = rangeRepository.findById(allocation.getRangeId()).orElseThrow(
                () -> new IllegalStateException("La asignación " + allocation.getId() + " apunta a un rango inexistente"));
        return toAllocation(allocation, range);
    }

    private static Allocation toAllocation(DocumentNumberAllocation allocation, NumberingRange range) {
        return new Allocation(allocation.getId(), allocation.getRangeId(), allocation.getPrefix(),
                allocation.getNumber(), range.getResolutionNumber(), range.getMinNumber(), range.getMaxNumber(),
                range.getStartDate(), range.getEndDate(),
                range.getTechnicalKey(), allocation.getIdempotencyKey(), allocation.isCompleted());
    }
}
