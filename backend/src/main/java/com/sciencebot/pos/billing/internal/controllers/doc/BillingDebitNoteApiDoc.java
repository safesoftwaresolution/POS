package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
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

@Tag(name = "📄 Facturación Electrónica - Notas Débito", description = "Emisión, validación y gestión de Notas Débito electrónicas ante Factus API v2 y la DIAN")
public interface BillingDebitNoteApiDoc {

    @Operation(
            summary = "Crear y validar nota débito",
            description = """
                    Crea y valida una nota débito electrónica ante Factus y la DIAN sobre una factura electrónica existente
                    (`customization_id=30`). Se usa para cobrar intereses, gastos adicionales o ajustar al alza el valor facturado.

                    **Comportamiento:**
                    - Envía la solicitud al endpoint `/v2/debit-notes/validate` de Factus.
                    - Si la referencia ya fue procesada y validada previamente, retorna los datos de la nota existente (**idempotencia**).
                    - En caso de conflicto `409` (documento pendiente en DIAN), elimina el borrador no validado y reintenta una vez.
                    - Si la DIAN rechaza la nota, se retorna con estado `REJECTED` y el motivo en `errorMessage`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota débito procesada (revisar `status`: VALIDATED, REJECTED o ERROR)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DebitNoteDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos de solicitud inválidos (ej. sin bill_number o customization_id distinto de 30)", content = @Content),
            @ApiResponse(responseCode = "409", description = "reference_code ya usado para otra factura u otro local", content = @Content)
    })
    ResponseEntity<DebitNoteDTO> createAndValidateDebitNote(CreateDebitNoteRequest request);

    @Operation(
            summary = "Listar notas débito de la tienda",
            description = "Retorna el historial paginado de notas débito para el local del usuario autenticado, con filtro opcional por estado."
    )
    ResponseEntity<Page<DebitNoteDTO>> listDebitNotes(
            @Parameter(description = "Paginación y ordenamiento") Pageable pageable,
            @Parameter(description = "Filtro opcional por estado: PENDING, VALIDATED, REJECTED, ERROR", example = "VALIDATED") String status
    );

    @Operation(
            summary = "Consultar nota débito por ID",
            description = "Retorna el detalle y estado fiscal de una nota débito por su ID interno."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota débito encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DebitNoteDTO.class))),
            @ApiResponse(responseCode = "404", description = "Nota débito no encontrada", content = @Content)
    })
    ResponseEntity<DebitNoteDTO> getDebitNoteById(
            @Parameter(description = "ID interno de la nota débito", example = "1", required = true) Long id
    );

    @Operation(
            summary = "Consultar nota débito por reference_code",
            description = "Retorna el detalle y estado fiscal de una nota débito por su código de referencia único."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota débito encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DebitNoteDTO.class))),
            @ApiResponse(responseCode = "404", description = "Nota débito no encontrada", content = @Content)
    })
    ResponseEntity<DebitNoteDTO> getDebitNoteByReferenceCode(
            @Parameter(description = "Código de referencia único", example = "ND-2026-0001", required = true) String referenceCode
    );

    @Operation(
            summary = "Descargar PDF de la nota débito",
            description = "Descarga el archivo PDF oficial emitido por Factus / DIAN para la nota débito indicada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF descargado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "404", description = "Nota débito no encontrada o PDF no disponible en Factus"),
            @ApiResponse(responseCode = "409", description = "La nota débito no ha sido validada")
    })
    ResponseEntity<byte[]> downloadPdf(
            @Parameter(description = "ID interno de la nota débito", example = "1", required = true) Long id
    );

    @Operation(
            summary = "Descargar XML legal de la nota débito",
            description = "Descarga el archivo XML firmado ante la DIAN para la nota débito indicada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "XML descargado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE)),
            @ApiResponse(responseCode = "404", description = "Nota débito no encontrada o XML no disponible en Factus"),
            @ApiResponse(responseCode = "409", description = "La nota débito no ha sido validada")
    })
    ResponseEntity<byte[]> downloadXml(
            @Parameter(description = "ID interno de la nota débito", example = "1", required = true) Long id
    );

    @Operation(
            summary = "Eliminar nota débito no validada",
            description = "Elimina de Factus una nota débito que quedó en conflicto o con errores y no fue validada ante la DIAN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Nota débito no validada eliminada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Factus no pudo eliminar la nota débito"),
            @ApiResponse(responseCode = "409", description = "La nota débito ya está validada por la DIAN")
    })
    ResponseEntity<Void> deleteUnvalidatedDebitNote(
            @Parameter(description = "Código de referencia único de la nota débito a eliminar", example = "ND-2026-0001", required = true) String referenceCode
    );

    @Operation(
            summary = "Crear nota débito sobre la factura de una venta",
            description = """
                    Emite una nota débito sobre la factura electrónica VALIDATED de una venta (ej. intereses o cobros adicionales).
                    El `bill_number` se toma de la factura de la venta (si se envía, debe coincidir) y la nota queda vinculada a la venta.
                    Si no se envía `customer`, Factus toma los datos del cliente de la factura referenciada.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nota débito procesada (revisar `status`)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DebitNoteDTO.class))),
            @ApiResponse(responseCode = "404", description = "La venta no tiene factura electrónica en este local"),
            @ApiResponse(responseCode = "409", description = "La factura de la venta no está VALIDATED")
    })
    ResponseEntity<DebitNoteDTO> createForSale(
            @Parameter(description = "ID de la venta", example = "5", required = true) Long saleId,
            CreateDebitNoteRequest request
    );
}
