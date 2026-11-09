package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceDto;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice.ReserveOutcome;
import com.sciencebot.pos.billing.internal.mappers.ElectronicInvoiceMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.dto.BillingEnvironment;

/**
 * Los pasos A y C de la emisión de una factura, cada uno en su propia transacción corta. La llamada al proveedor
 * (paso B) ocurre entre ambos, sin transacción ni lock tomado. Es un bean aparte para que {@code @Transactional}
 * pase por el proxy (una auto-invocación no abriría la transacción).
 */
@Component
public class InvoiceEmissionTransactions {

    /** Resultado del paso A: quién emite y, si no es este hilo, el estado actual de la factura. */
    public record Reservation(ReserveOutcome outcome, ElectronicInvoiceDto invoice, String reconcileProviderDocumentId,
                              BillingDocumentContext context) {
        public Reservation(ReserveOutcome outcome, ElectronicInvoiceDto invoice, String reconcileProviderDocumentId) {
            this(outcome, invoice, reconcileProviderDocumentId, null);
        }
    }

    @Value("${billing.provider:alegra}")
    private String activeProviderName = "alegra";

    private final ElectronicInvoiceRepository repository;
    private final ElectronicInvoiceMapper mapper;
    /** De aquí sale el entorno del local que se anota en la factura al reservar su emisión. */
    private final StoreBillingConfigRepository configRepository;
    private final Duration lease;

    @Autowired
    public InvoiceEmissionTransactions(ElectronicInvoiceRepository repository, ElectronicInvoiceMapper mapper,
                                       StoreBillingConfigRepository configRepository,
                                       @Value("${billing.emission.lease:PT5M}") Duration lease) {
        this.repository = repository;
        this.mapper = mapper;
        this.configRepository = configRepository;
        this.lease = lease;
    }

    /** Paso A: bloquea (o crea) la factura de la venta y toma la reserva de emisión. */
    @Transactional
    public Reservation reserve(Long saleId, Long storeId) {
        ElectronicInvoice invoice = repository.findBySaleIdForUpdate(saleId)
                .orElseGet(() -> repository.saveAndFlush(ElectronicInvoice.initialize(saleId, storeId)));
        invoice.assignStoreIdIfMissing(storeId);
        if (!storeId.equals(invoice.getStoreId())) {
            throw new IllegalStateException("La factura no pertenece al local de la venta");
        }
        // Si el proveedor ya recibió el documento, quien emite lo concilia por su id en vez de emitirlo otra vez.
        String reconcile = invoice.awaitsProviderResponse() ? invoice.getProviderDocumentId() : null;
        ReserveOutcome outcome = invoice.reserveForEmission(LocalDateTime.now(), lease);
        if (outcome == ReserveOutcome.RESERVED) {
            assignStoreEnvironment(invoice);
        }
        repository.save(invoice);
        BillingDocumentContext context = invoice.getProviderName() != null ? invoice.documentContext() : null;
        return new Reservation(outcome, mapper.toDto(invoice), reconcile, context);
    }

    /** Paso C: vuelve a bloquear la fila, aplica el resultado del proveedor y programa el reintento que corresponda. */
    @Transactional
    public ElectronicInvoiceDto complete(Long saleId, InvoiceResult result) {
        ElectronicInvoice invoice = repository.findBySaleIdForUpdate(saleId).orElseThrow(
                () -> new IllegalStateException("No existe la factura de la venta " + saleId + " para registrar el resultado"));
        if (invoice.isValidated()) {
            // Otro hilo ya la validó (por ejemplo, tras vencer la reserva): es fiscalmente inmutable.
            return mapper.toDto(invoice);
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime retryAt = null;
        try {
            if (result != null) {
                invoice.trackProviderDocument(result.providerDocumentId());
            }
            retryAt = apply(invoice, result, now);
        } catch (IllegalStateException incomplete) {
            invoice.markAsError("Respuesta incompleta del proveedor de facturación: " + incomplete.getMessage());
            retryAt = EmissionRetryPolicy.nextRetryAt(invoice.getAttempts(), now);
        }
        invoice.completeAttempt(retryAt);
        repository.save(invoice);
        return mapper.toDto(invoice);
    }

    /** Reinicio manual (reintento del usuario): vuelve a PENDING y borra el conteo, sin tocar una reserva vigente. */
    @Transactional
    public void resetForRetry(Long saleId, Long storeId) {
        repository.findBySaleIdAndStoreId(saleId, storeId).ifPresent(invoice -> {
            invoice.prepareForRetry();
            repository.save(invoice);
        });
    }

    /** La factura que se va a emitir toma el entorno actual de su local, si aún no tiene uno. */
    private void assignStoreEnvironment(ElectronicInvoice invoice) {
        if (invoice.getProviderName() != null || invoice.getStoreId() == null) {
            return;
        }
        if (!"alegra".equalsIgnoreCase(activeProviderName)) {
            invoice.assignDocumentContext(new BillingDocumentContext(invoice.getStoreId(),
                    invoice.getEnvironment() != null ? invoice.getEnvironment() : BillingEnvironment.SANDBOX,
                    activeProviderName, null));
            return;
        }
        // Only a first, never-sent attempt may adopt today's configuration. Historical context is never guessed.
        if (invoice.getAttempts() != 1 || invoice.getProviderDocumentId() != null) return;
        configRepository.findById(invoice.getStoreId()).ifPresent(config -> {
            BillingEnvironment environment = invoice.getEnvironment() != null ? invoice.getEnvironment() : config.getEnvironment();
            invoice.assignEnvironmentIfMissing(environment);
            String companyId = config.companyIdFor(environment);
            if (companyId != null && (invoice.getEnvironment() == null || invoice.getEnvironment() == config.getEnvironment())) {
                invoice.assignDocumentContext(new BillingDocumentContext(invoice.getStoreId(), environment, activeProviderName, companyId));
            }
        });
    }

    /** Aplica el resultado y devuelve cuándo reintentar (null si no debe reintentarse sola). */
    private static LocalDateTime apply(ElectronicInvoice invoice, InvoiceResult result, LocalDateTime now) {
        if (result == null) {
            invoice.markAsError("Respuesta vacía del proveedor de facturación");
            return EmissionRetryPolicy.nextRetryAt(invoice.getAttempts(), now);
        }
        if ("VALIDATED".equalsIgnoreCase(result.status())) {
            invoice.markAsValidated(result.legalInvoiceNumber(), result.cufe(), result.qrCode(), result.pdfUrl(),
                    result.validatedAt());
            return null;
        }
        if ("REJECTED".equalsIgnoreCase(result.status())) {
            invoice.markAsRejected(result.errorMessage());
            return null; // un rechazo exige corregir datos: no se reintenta solo
        }
        if (InvoiceResult.NOT_ENABLED.equals(result.status())) {
            invoice.markAsError(result.errorMessage());
            return null; // el local no puede facturar: reintentar sola no lo arregla
        }
        if ("PENDING".equalsIgnoreCase(result.status())) {
            invoice.markAsPending(result.errorMessage());
        } else {
            invoice.markAsError(result.errorMessage());
        }
        return EmissionRetryPolicy.nextRetryAt(invoice.getAttempts(), now);
    }
}
