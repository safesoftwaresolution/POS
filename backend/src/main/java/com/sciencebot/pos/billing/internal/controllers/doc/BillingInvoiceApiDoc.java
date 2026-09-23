package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceDto;
import com.sciencebot.pos.billing.dto.SendInvoiceEmailRequest;
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

@Tag(name = "📄 Facturación Electrónica - Facturas", description = "Emisión, consulta y reintento de facturas electrónicas de venta ante la DIAN / Factus")
public interface BillingInvoiceApiDoc {

    @Operation(
            summary = "Listar facturas electrónicas de la tienda",
            description = "Retorna el historial paginado de facturas electrónicas para la tienda del usuario autenticado, con filtro opcional por estado."
    )
    ResponseEntity<Page<ElectronicInvoiceDto>> listInvoices(
            @Parameter(description = "Paginación y ordenamiento") Pageable pageable,
            @Parameter(description = "Filtro opcional por estado: PENDING, VALIDATED, ERROR", example = "VALIDATED") String status
    );

    @Operation(
            summary = "Consultar factura electrónica de una venta",
            description = """
                    Retorna el estado y datos de la factura electrónica generada para una venta específica.
                    
                    **Campos clave:**
                    - `status`: `PENDING`, `VALIDATED`, `ERROR`
                    - `cufe`: Código Único de Factura Electrónica (DIAN)
                    - `pdfUrl`: URL al PDF de la factura en Factus
                    - `qrCode`: Código QR para validación DIAN
                    - `errorMessage`: Detalle del error si el status es `ERROR`
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Factura electrónica encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ElectronicInvoiceDto.class))),
            @ApiResponse(responseCode = "404", description = "No existe factura electrónica para esta venta", content = @Content)
    })
    ResponseEntity<ElectronicInvoiceDto> getInvoiceBySaleId(
            @Parameter(description = "ID de la venta", example = "1", required = true) Long saleId
    );

    @Operation(
            summary = "Reintentar facturación electrónica",
            description = """
                    Reintenta el envío de la factura electrónica a Factus para una venta que haya fallado (`status = ERROR`).
                    
                    Útil cuando:
                    - Hubo un error de conexión con Factus durante la venta
                    - La factura fue rechazada por errores de validación DIAN
                    
                    Requiere rol **ADMINISTRATOR** o **SUPERVISOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reintento procesado — retorna el estado actualizado de la factura",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ElectronicInvoiceDto.class))),
            @ApiResponse(responseCode = "404", description = "Venta no encontrada", content = @Content),
            @ApiResponse(responseCode = "400", description = "La factura ya está validada y no puede reintentarse", content = @Content)
    })
    ResponseEntity<ElectronicInvoiceDto> retryInvoice(
            @Parameter(description = "ID de la venta a re-facturar", example = "1", required = true) Long saleId
    );

    @Operation(
            summary = "Enviar factura electrónica por correo electrónico",
            description = """
                    Envía el archivo ZIP con el PDF y XML legal de la factura electrónica
                    al correo electrónico indicado en el cuerpo de la petición.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Correo enviado exitosamente"),
            @ApiResponse(responseCode = "400", description = "La factura no ha sido validada por la DIAN o datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Venta o factura no encontrada")
    })
    ResponseEntity<Void> sendInvoiceEmail(
            @Parameter(description = "ID de la venta cuya factura se enviará", example = "1", required = true) Long saleId,
            SendInvoiceEmailRequest request
    );

    @Operation(
            summary = "Descargar PDF de la factura electrónica",
            description = "Descarga el archivo PDF legal emitido por Factus / DIAN para la venta especificada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF descargado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE)),
            @ApiResponse(responseCode = "404", description = "Venta o factura no encontrada"),
            @ApiResponse(responseCode = "400", description = "La factura no ha sido validada")
    })
    ResponseEntity<byte[]> downloadPdf(
            @Parameter(description = "ID de la venta", example = "1", required = true) Long saleId
    );

    @Operation(
            summary = "Descargar XML legal de la factura electrónica",
            description = "Descarga el archivo XML firmado ante la DIAN para la venta especificada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "XML descargado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE)),
            @ApiResponse(responseCode = "404", description = "Venta o factura no encontrada"),
            @ApiResponse(responseCode = "400", description = "La factura no ha sido validada")
    })
    ResponseEntity<byte[]> downloadXml(
            @Parameter(description = "ID de la venta", example = "1", required = true) Long saleId
    );
}
