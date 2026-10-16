package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Tag(name = "📄 Facturación Electrónica - Notas Crédito", description = "Emisión, validación y gestión de Notas Crédito electrónicas ante Factus API v2 y la DIAN")
public interface BillingCreditNoteApiDoc {

    @Operation(
            summary = "Crear y validar nota crédito",
            description = """
                    Crea y valida una nota crédito electrónica ante Factus y la DIAN.
                    
                    **Comportamiento:**
                    - Envía la solicitud al endpoint `/v2/credit-notes/validate` de Factus.
                    - Si la referencia ya fue procesada y validada previamente, retorna los datos de la nota existente (**idempotencia**).
                    - En caso de conflicto `409` (documento pendiente en DIAN), se gestiona la limpieza de notas no validadas.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota crédito validada o recuperada exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreditNoteDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de solicitud inválidos o rechazo de validación", content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflicto con documento pendiente o facturación no habilitada", content = @Content)
    })
    ResponseEntity<CreditNoteDTO> createAndValidateCreditNote(CreateCreditNoteRequest request);

    @Operation(
            summary = "Listar notas crédito de la tienda",
            description = "Retorna el historial paginado de notas crédito para el local del usuario autenticado, con filtro opcional por estado."
    )
    ResponseEntity<Page<CreditNoteDTO>> listCreditNotes(
            @Parameter(description = "Paginación y ordenamiento") Pageable pageable,
            @Parameter(description = "Filtro opcional por estado: PENDING, VALIDATED, REJECTED, ERROR", example = "VALIDATED") String status
    );

    @Operation(
            summary = "Consultar nota crédito por ID",
            description = "Retorna el detalle y estado fiscal de una nota crédito por su ID interno."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota crédito encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreditNoteDTO.class))),
            @ApiResponse(responseCode = "404", description = "Nota crédito no encontrada", content = @Content)
    })
    ResponseEntity<CreditNoteDTO> getCreditNoteById(
            @Parameter(description = "ID interno de la nota crédito", example = "1", required = true) Long id
    );

    @Operation(
            summary = "Consultar nota crédito por reference_code",
            description = "Retorna el detalle y estado fiscal de una nota crédito por su código de referencia único."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota crédito encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreditNoteDTO.class))),
            @ApiResponse(responseCode = "404", description = "Nota crédito no encontrada", content = @Content)
    })
    ResponseEntity<CreditNoteDTO> getCreditNoteByReferenceCode(
            @Parameter(description = "Código de referencia único", example = "NOTA-2026-0015", required = true) String referenceCode
    );

    @Operation(
            summary = "Descargar PDF de la nota crédito",
            description = "Descarga el archivo PDF oficial emitido por Factus / DIAN para la nota crédito indicada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF descargado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "404", description = "Nota crédito no encontrada"),
            @ApiResponse(responseCode = "400", description = "La nota crédito no ha sido validada")
    })
    ResponseEntity<byte[]> downloadPdf(
            @Parameter(description = "ID interno de la nota crédito", example = "1", required = true) Long id
    );

    @Operation(
            summary = "Descargar XML legal de la nota crédito",
            description = "Descarga el archivo XML firmado ante la DIAN para la nota crédito indicada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "XML descargado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE)),
            @ApiResponse(responseCode = "404", description = "Nota crédito no encontrada"),
            @ApiResponse(responseCode = "400", description = "La nota crédito no ha sido validada")
    })
    ResponseEntity<byte[]> downloadXml(
            @Parameter(description = "ID interno de la nota crédito", example = "1", required = true) Long id
    );

    @Operation(
            summary = "Eliminar nota crédito no validada",
            description = "Elimina de Factus una nota crédito que quedó en conflicto o con errores y no fue validada ante la DIAN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Nota crédito no validada eliminada exitosamente"),
            @ApiResponse(responseCode = "400", description = "La nota crédito ya está validada o no se pudo eliminar")
    })
    ResponseEntity<Void> deleteUnvalidatedCreditNote(
            @Parameter(description = "Código de referencia único de la nota crédito a eliminar", example = "NOTA-2026-0015", required = true) String referenceCode
    );

    @Operation(
            summary = "Crear nota crédito a partir de una venta",
            description = """
                    Genera y valida automáticamente una nota crédito para una venta con factura electrónica previa VALIDATED.
                    Útil para anulaciones directas o devoluciones registradas desde la caja POS.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota crédito creada y validada exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CreditNoteDTO.class))),
            @ApiResponse(responseCode = "404", description = "Venta o factura no encontrada"),
            @ApiResponse(responseCode = "400", description = "La factura no está validada o datos inválidos")
    })
    ResponseEntity<CreditNoteDTO> createFromSale(
            @Parameter(description = "ID de la venta", example = "5", required = true) Long saleId,
            @Parameter(description = "Código de corrección (ej. 2=Anulación)", example = "2") String conceptCode,
            @Parameter(description = "Observación o motivo", example = "Anulación solicitada por el cliente") String observation
    );
}
