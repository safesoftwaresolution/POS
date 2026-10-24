package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface DebitNoteService {

    /**
     * Crea y valida una nota débito electrónica ante Factus y la DIAN.
     */
    DebitNoteDTO createAndValidateDebitNote(CreateDebitNoteRequest request);

    /**
     * Consulta una nota débito por su identificador interno.
     */
    Optional<DebitNoteDTO> getById(Long id);

    /**
     * Consulta una nota débito por su código de referencia único.
     */
    Optional<DebitNoteDTO> getByReferenceCode(String referenceCode);

    /**
     * Lista notas débito del local con paginación y filtro opcional por estado.
     */
    Page<DebitNoteDTO> listDebitNotes(Pageable pageable, String status);

    /**
     * Descarga el PDF legal de la nota débito.
     */
    byte[] downloadDebitNotePDF(Long id);

    /**
     * Descarga el XML legal de la nota débito.
     */
    byte[] downloadDebitNoteXML(Long id);

    /**
     * Elimina una nota débito no validada en Factus (para resolver conflictos 409).
     */
    boolean deleteUnvalidatedDebitNote(String referenceCode);

    /**
     * Emite una nota débito sobre la factura electrónica VALIDATED de una venta (ej. intereses o
     * cobros adicionales). El {@code bill_number} se toma de la factura de la venta y la nota queda
     * vinculada a ella para trazabilidad.
     */
    DebitNoteDTO createForSale(Long saleId, CreateDebitNoteRequest request);
}
