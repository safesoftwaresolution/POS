package com.sciencebot.pos.sales.internal.controllers;

import com.sciencebot.pos.sales.CreateSaleCommand;
import com.sciencebot.pos.sales.CreateSaleReturnCommand;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import com.sciencebot.pos.sales.SaleReturnDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sales")
@Tag(name = "💰 Ventas", description = "Registro y consulta de transacciones de venta")
public class SaleController {

    private final SaleFacade saleFacade;

    public SaleController(SaleFacade saleFacade) {
        this.saleFacade = saleFacade;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Registrar venta",
            description = """
                    Crea una nueva venta en el sistema. Proceso:
                    1. Valida stock disponible para cada ítem
                    2. Descuenta inventario automáticamente
                    3. Genera número de factura interno
                    4. Dispara el proceso de facturación electrónica con Factus (asíncrono)
                    
                    El `customerId` es opcional — si no se envía, la venta se registra como venta a consumidor final.

                    **Idempotencia:** envíe el header `Idempotency-Key` (un UUID por intento de cobro). Un
                    reenvío con la misma clave (doble clic o reintento de red) devuelve la venta original en
                    lugar de crear un duplicado ni descontar inventario dos veces.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Venta registrada exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SaleDto.class))),
            @ApiResponse(responseCode = "400", description = "Stock insuficiente o datos inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<SaleDto> registerSale(
            @Parameter(description = "Clave de idempotencia (UUID) para evitar ventas duplicadas por reenvío")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateSaleCommand command) {
        try {
            SaleDto created = saleFacade.registerSale(command, idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (DataIntegrityViolationException ex) {
            // Reenvío concurrente: otro request con la misma Idempotency-Key ganó la carrera y
            // esta transacción se revirtió por completo. Devolvemos la venta ya persistida.
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                return saleFacade.findByIdempotencyKey(idempotencyKey)
                        .map(existing -> ResponseEntity.status(HttpStatus.CREATED).body(existing))
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Buscar ventas",
            description = """
                    Lista ventas con filtros opcionales. Soporta paginación.
                    - `customerId` — filtra por cliente específico
                    - `dateFrom` y `dateTo` — rango de fechas en formato ISO 8601: `2025-01-15T00:00:00`
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de ventas",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    public ResponseEntity<Page<SaleDto>> searchSales(
            @Parameter(description = "Filtrar por ID de cliente", example = "3")
            @RequestParam(required = false) Long customerId,
            @Parameter(description = "Fecha inicial del rango (ISO 8601)", example = "2025-01-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "Fecha final del rango (ISO 8601)", example = "2025-12-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @Parameter(description = "Número de página (desde 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SaleDto> sales = saleFacade.searchSales(customerId, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(sales);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Obtener venta por ID",
            description = "Retorna el detalle completo de una venta incluyendo todos sus ítems."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Venta encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SaleDto.class))),
            @ApiResponse(responseCode = "404", description = "Venta no encontrada", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    public ResponseEntity<SaleDto> getSaleById(
            @Parameter(description = "ID de la venta", example = "1", required = true)
            @PathVariable Long id) {
        return saleFacade.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/returns")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Registrar devolución de una venta",
            description = """
                    Devuelve uno o varios ítems de una venta ya registrada. La venta original
                    **nunca se modifica** (sigue siendo el comprobante fiscal íntegro); esto crea un
                    registro de devolución aparte que:
                    1. Valida que cada ítem pertenezca a esta venta y que la cantidad no exceda lo
                       vendido menos lo ya devuelto en devoluciones previas de esa misma línea.
                    2. Repone el stock automáticamente (movimiento `DEVOLUCION_VENTA`).
                    3. Calcula el monto a reembolsar al precio al que se vendió cada ítem.

                    Cada ítem se referencia por el `id` de su línea en la venta original
                    (`SaleItemDto.id`), no por `productId` — así se distingue correctamente si el
                    mismo producto aparece en más de una línea.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Devolución registrada exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SaleReturnDto.class))),
            @ApiResponse(responseCode = "400", description = "Cantidad a devolver inválida o ítem ajeno a la venta", content = @Content),
            @ApiResponse(responseCode = "404", description = "Venta o ítem no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<SaleReturnDto> registerReturn(
            @Parameter(description = "ID de la venta original", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody CreateSaleReturnCommand command) {
        SaleReturnDto created = saleFacade.registerReturn(id, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}/returns")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Listar devoluciones de una venta",
            description = "Retorna todas las devoluciones registradas sobre esta venta, más recientes primero."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devoluciones de la venta",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = SaleReturnDto.class)))),
            @ApiResponse(responseCode = "404", description = "Venta no encontrada", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    public ResponseEntity<List<SaleReturnDto>> getReturnsBySale(
            @Parameter(description = "ID de la venta original", example = "1", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(saleFacade.getReturnsBySale(id));
    }
}
