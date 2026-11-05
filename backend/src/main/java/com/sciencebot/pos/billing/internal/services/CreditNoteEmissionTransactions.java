package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.entities.ElectronicCreditNote;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice.ReserveOutcome;
import com.sciencebot.pos.billing.internal.mappers.ElectronicCreditNoteMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicCreditNoteRepository;
import com.sciencebot.pos.shared.validation.Preconditions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Los pasos A y C de la emision de una nota credito, cada uno en su propia transaccion corta. La llamada al proveedor
 * (paso B) ocurre entre ambos, sin transaccion ni lock tomado. Es un bean aparte para que {@code @Transactional}
 * pase por el proxy (una auto-invocacion no abriria la transaccion).
 */
@Component
public class CreditNoteEmissionTransactions {

    /** Resultado del paso A: quien emite y, si no es este hilo, el estado actual de la nota. */
    public record Reservation(ReserveOutcome outcome, CreditNoteDTO note) {}

    private final ElectronicCreditNoteRepository repository;
    private final ElectronicCreditNoteMapper mapper;
    private final Duration lease;

    public CreditNoteEmissionTransactions(ElectronicCreditNoteRepository repository, ElectronicCreditNoteMapper mapper,
                                          @Value("${billing.emission.lease:PT5M}") Duration lease) {
        this.repository = repository;
        this.mapper = mapper;
        this.lease = lease;
    }

    /** Paso A: bloquea (o crea) la nota de la referencia y toma la reserva de emision. */
    @Transactional
    public Reservation reserve(CreateCreditNoteRequest request, Long saleId, Long storeId) {
        Optional<ElectronicCreditNote> existingOpt = repository.findByReferenceCodeForUpdate(request.referenceCode());
        ElectronicCreditNote note;
        if (existingOpt.isPresent()) {
            note = existingOpt.get();
            // reference_code es UNIQUE global: nunca se reutiliza ni se expone la nota de otro local
            Preconditions.checkState(storeId.equals(note.getStoreId()),
                    "El reference_code " + request.referenceCode() + " ya está en uso");
        } else {
            note = repository.saveAndFlush(ElectronicCreditNote.initialize(
                    storeId,
                    saleId,
                    request.referenceCode(),
                    request.billNumber(),
                    request.correctionConceptCode(),
                    request.customizationId()
            ));
        }
        ReserveOutcome outcome = note.reserveForEmission(LocalDateTime.now(), lease);
        if (outcome == ReserveOutcome.RESERVED) {
            repository.save(note);
        }
        return new Reservation(outcome, mapper.toDto(note));
    }

    /** Paso C: vuelve a bloquear la fila, aplica el resultado del proveedor y libera la reserva. */
    @Transactional
    public CreditNoteDTO complete(String referenceCode, Long storeId, CreditNoteResult result) {
        ElectronicCreditNote note = repository.findByReferenceCodeForUpdate(referenceCode)
                .filter(n -> storeId.equals(n.getStoreId()))
                .orElseThrow(() -> new IllegalStateException(
                        "No existe la nota crédito " + referenceCode + " para registrar el resultado"));
        if (note.isValidated()) {
            // Otro hilo ya la valido (por ejemplo, tras vencer la reserva): es fiscalmente inmutable.
            return mapper.toDto(note);
        }
        try {
            apply(note, result);
        } catch (IllegalStateException incomplete) {
            note.markAsError("Respuesta incompleta del proveedor de facturación: " + incomplete.getMessage());
        }
        note.completeAttempt();
        return mapper.toDto(repository.save(note));
    }

    private static void apply(ElectronicCreditNote note, CreditNoteResult result) {
        if (result == null) {
            note.markAsError("Respuesta vacía del proveedor de facturación");
            return;
        }
        note.trackProviderDocument(result.providerDocumentId());
        if ("VALIDATED".equalsIgnoreCase(result.status())) {
            note.markAsValidated(result.creditNoteNumber(), result.cude(), result.qrCode(), result.pdfUrl(),
                    result.validatedAt());
        } else if ("REJECTED".equalsIgnoreCase(result.status())) {
            note.markAsRejected(result.errorMessage());
        } else {
            note.markAsError(result.errorMessage());
        }
    }
}
