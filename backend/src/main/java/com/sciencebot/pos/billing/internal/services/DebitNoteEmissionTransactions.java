package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice.ReserveOutcome;
import com.sciencebot.pos.billing.internal.mappers.ElectronicDebitNoteMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicDebitNoteRepository;
import com.sciencebot.pos.shared.validation.Preconditions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Los pasos A y C de la emision de una nota debito, cada uno en su propia transaccion corta. La llamada al proveedor
 * (paso B) ocurre entre ambos, sin transaccion ni lock tomado. Es un bean aparte para que {@code @Transactional}
 * pase por el proxy (una auto-invocacion no abriria la transaccion).
 */
@Component
public class DebitNoteEmissionTransactions {

    /** Resultado del paso A: quien emite y, si no es este hilo, el estado actual de la nota. */
    public record Reservation(ReserveOutcome outcome, DebitNoteDTO note, BillingDocumentContext context,
                              String reconcileProviderDocumentId) {
        public Reservation(ReserveOutcome outcome, DebitNoteDTO note) { this(outcome, note, null, null); }
    }

    private final ElectronicDebitNoteRepository repository;
    private final ElectronicDebitNoteMapper mapper;
    private final Duration lease;
    private final ElectronicInvoiceRepository invoiceRepository;
    @Value("${billing.provider:alegra}")
    private String activeProviderName = "alegra";

    public DebitNoteEmissionTransactions(ElectronicDebitNoteRepository repository, ElectronicDebitNoteMapper mapper,
                                         @Value("${billing.emission.lease:PT5M}") Duration lease) {
        this(repository, mapper, lease, null);
    }

    @Autowired
    public DebitNoteEmissionTransactions(ElectronicDebitNoteRepository repository, ElectronicDebitNoteMapper mapper,
            @Value("${billing.emission.lease:PT5M}") Duration lease, ElectronicInvoiceRepository invoiceRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.lease = lease;
        this.invoiceRepository = invoiceRepository;
    }

    /** Paso A: bloquea (o crea) la nota de la referencia y toma la reserva de emision. */
    @Transactional
    public Reservation reserve(CreateDebitNoteRequest request, Long saleId, Long storeId) {
        String referenceCode = request.referenceCode();
        String billNumber = request.billNumber();
        Optional<ElectronicDebitNote> existingOpt = repository.findByReferenceCodeForUpdate(referenceCode);
        ElectronicDebitNote note;
        if (existingOpt.isPresent()) {
            note = existingOpt.get();
            // reference_code es UNIQUE global: nunca se reutiliza ni se expone la nota de otro local
            Preconditions.checkState(storeId.equals(note.getStoreId()),
                    "El reference_code " + referenceCode + " ya está en uso");
            Preconditions.checkState(billNumber.equals(note.getBillNumber()),
                    "El reference_code " + referenceCode + " ya fue usado para la factura " + note.getBillNumber());
        } else {
            note = repository.saveAndFlush(ElectronicDebitNote.initialize(
                    storeId,
                    saleId,
                    referenceCode,
                    billNumber,
                    request.correctionConceptCode(),
                    request.customizationId()
            ));
        }
        captureContext(note, saleId, storeId);
        ReserveOutcome outcome = note.reserveForEmission(LocalDateTime.now(), lease);
        if (outcome == ReserveOutcome.RESERVED) {
            repository.save(note);
        }
        return reservation(note, outcome);
    }

    /** Paso C: vuelve a bloquear la fila, aplica el resultado del proveedor y libera la reserva. */
    @Transactional
    public DebitNoteDTO complete(String referenceCode, Long storeId, DebitNoteResult result) {
        ElectronicDebitNote note = repository.findByReferenceCodeForUpdate(referenceCode)
                .filter(n -> storeId.equals(n.getStoreId()))
                .orElseThrow(() -> new IllegalStateException(
                        "No existe la nota débito " + referenceCode + " para registrar el resultado"));
        if (note.isValidated()) {
            // Otro hilo ya la valido (por ejemplo, tras vencer la reserva): es fiscalmente inmutable.
            return mapper.toDto(note);
        }
        try {
            apply(note, result);
        } catch (IllegalStateException | IllegalArgumentException incomplete) {
            note.markAsError("Respuesta incompleta del proveedor de facturación: " + incomplete.getMessage());
        }
        note.completeAttempt();
        return mapper.toDto(repository.save(note));
    }

    @Transactional
    public Reservation reserveRetry(Long id, Long storeId) {
        ElectronicDebitNote selected = repository.findById(id).filter(n -> storeId.equals(n.getStoreId()))
                .orElseThrow(() -> new IllegalArgumentException("Nota no encontrada"));
        ElectronicDebitNote note = repository.findByReferenceCodeForUpdate(selected.getReferenceCode())
                .filter(n -> storeId.equals(n.getStoreId())).orElseThrow();
        ReserveOutcome outcome = note.reserveForEmission(LocalDateTime.now(), lease);
        repository.save(note);
        return reservation(note, outcome);
    }

    private Reservation reservation(ElectronicDebitNote note, ReserveOutcome outcome) {
        return new Reservation(outcome, mapper.toDto(note),
                note.getProviderName() == null ? null : note.documentContext(), note.getProviderDocumentId());
    }

    private void captureContext(ElectronicDebitNote note, Long saleId, Long storeId) {
        if (note.getProviderName() != null || note.getAttempts() > 0) return;
        if (!"alegra".equalsIgnoreCase(activeProviderName)) {
            note.assignDocumentContext(new BillingDocumentContext(storeId, BillingEnvironment.SANDBOX, activeProviderName, null));
        } else if (invoiceRepository != null) {
            var source = saleId != null ? invoiceRepository.findBySaleIdAndStoreId(saleId, storeId)
                    : invoiceRepository.findByStoreIdAndLegalNumber(storeId, note.getBillNumber());
            source.filter(invoice -> invoice.getProviderName() != null && invoice.isValidated())
                    .ifPresent(invoice -> note.assignDocumentContext(invoice.documentContext()));
        }
    }
    private static void apply(ElectronicDebitNote note, DebitNoteResult result) {
        if (result == null) {
            note.markAsError("Respuesta vacía del proveedor de facturación");
            return;
        }
        note.trackProviderDocument(result.providerDocumentId());
        if ("VALIDATED".equalsIgnoreCase(result.status())) {
            if (result.debitNoteNumber() == null || result.debitNoteNumber().isBlank()) {
                note.markAsError("El proveedor reportó la nota débito como validada pero no retornó su número legal");
                return;
            }
            note.markAsValidated(result.debitNoteNumber(), result.cude(), result.qrCode(), result.pdfUrl(),
                    result.validatedAt());
        } else if ("REJECTED".equalsIgnoreCase(result.status())) {
            note.markAsRejected(Preconditions.defaultIfBlank(result.errorMessage(), "Nota débito rechazada por la DIAN"));
        } else if ("PENDING".equalsIgnoreCase(result.status())) {
            note.markAsPending(result.errorMessage());
        } else {
            note.markAsError(Preconditions.defaultIfBlank(result.errorMessage(), "Error en proceso de nota débito"));
        }
    }
}
