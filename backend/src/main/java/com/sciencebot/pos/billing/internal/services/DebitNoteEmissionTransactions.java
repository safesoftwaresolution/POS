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

/**
 * Los pasos A y C de la emision de una nota debito, cada uno en su propia transaccion corta. La llamada al proveedor
 * (paso B) ocurre entre ambos, sin transaccion ni lock tomado. Es un bean aparte para que {@code @Transactional}
 * pase por el proxy (una auto-invocacion no abriria la transaccion).
 */
@Component
public class DebitNoteEmissionTransactions {

    /** Resultado del paso A: quien emite y, si no es este hilo, el estado actual de la nota. */
    public record Reservation(ReserveOutcome outcome, DebitNoteDTO note) {}

    private final ElectronicDebitNoteRepository repository;
    private final ElectronicDebitNoteMapper mapper;
    private final Duration lease;

    public DebitNoteEmissionTransactions(ElectronicDebitNoteRepository repository, ElectronicDebitNoteMapper mapper,
                                         @Value("${billing.emission.lease:PT5M}") Duration lease) {
        this.repository = repository;
        this.mapper = mapper;
        this.lease = lease;
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
        ReserveOutcome outcome = note.reserveForEmission(LocalDateTime.now(), lease);
        if (outcome == ReserveOutcome.RESERVED) {
            repository.save(note);
        }
        return new Reservation(outcome, mapper.toDto(note));
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
        } else {
            note.markAsError(Preconditions.defaultIfBlank(result.errorMessage(), "Error en proceso de nota débito"));
        }
    }
}
