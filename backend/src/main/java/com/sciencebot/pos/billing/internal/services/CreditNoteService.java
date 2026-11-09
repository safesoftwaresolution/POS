package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CreditNoteService {

    /** Reconcile or replay the original request without creating another legal document. */
    CreditNoteDTO retry(Long id);

    /**
     * Crea y valida una nota crédito electrónica ante la DIAN.
     */
    CreditNoteDTO createAndValidateCreditNote(CreateCreditNoteRequest request);

    /**
     * Consulta una nota crédito por su identificador interno.
     */
    Optional<CreditNoteDTO> getById(Long id);

    /**
     * Consulta una nota crédito por su código de referencia único.
     */
    Optional<CreditNoteDTO> getByReferenceCode(String referenceCode);

    /**
     * Lista notas crédito del local con paginación y filtro opcional por estado.
     */
    Page<CreditNoteDTO> listCreditNotes(Pageable pageable, String status);

    /**
     * Descarga el PDF legal de la nota crédito.
     */
    byte[] downloadCreditNotePDF(Long id);

    /**
     * Descarga el XML legal de la nota crédito.
     */
    byte[] downloadCreditNoteXML(Long id);

    /**
     * Genera automáticamente una nota crédito para una venta específica (ej. anulación o devolución).
     */
    CreditNoteDTO createFromSale(Long saleId, String conceptCode, String observation);
}
