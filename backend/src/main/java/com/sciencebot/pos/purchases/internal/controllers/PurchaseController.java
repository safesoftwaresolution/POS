package com.sciencebot.pos.purchases.internal.controllers;

import com.sciencebot.pos.purchases.CreatePurchaseCommand;
import com.sciencebot.pos.purchases.PurchaseDto;
import com.sciencebot.pos.purchases.PurchaseFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@RestController
@RequestMapping("/api/v1/purchases")
@Tag(name = "🧾 Compras", description = "Registro y consulta de órdenes de compra a proveedores")
public class PurchaseController {

    private final PurchaseFacade purchaseFacade;

    public PurchaseController(PurchaseFacade purchaseFacade) {
        this.purchaseFacade = purchaseFacade;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Registrar compra",
            description = """
                    Registra una nueva orden de compra a un proveedor. Al registrar una compra:
                    1. Se crea el registro de la compra con sus ítems
                    2. Se actualiza automáticamente el stock de cada producto comprado (`ENTRY` en inventario)
                    3. Se actualiza el precio de compra del producto
                    
                    El `invoiceNumber` corresponde al número de factura del proveedor y no puede repetirse
                    para el mismo proveedor (se rechaza con `409 Conflict`).

                    **Idempotencia:** envíe el header `Idempotency-Key` (un UUID por registro). Un reenvío con
                    la misma clave devuelve la compra original en lugar de duplicar el stock.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Compra registrada exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PurchaseDto.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o proveedor inexistente", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content),
            @ApiResponse(responseCode = "409", description = "Factura de proveedor ya registrada", content = @Content)
    })
    public ResponseEntity<PurchaseDto> registerPurchase(
            @Parameter(description = "Clave de idempotencia (UUID) para evitar compras duplicadas por reenvío")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreatePurchaseCommand command) {
        try {
            PurchaseDto created = purchaseFacade.registerPurchase(command, idempotencyKey);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (DataIntegrityViolationException ex) {
            // Reenvío concurrente con la misma Idempotency-Key: la transacción perdedora se
            // revirtió por completo; devolvemos la compra ya persistida.
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                return purchaseFacade.findByIdempotencyKey(idempotencyKey)
                        .map(existing -> ResponseEntity.status(HttpStatus.CREATED).body(existing))
                        .orElseThrow(() -> ex);
            }
            // Sin clave, una violación aquí es factura de proveedor duplicada → 409 vía handler.
            throw ex;
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Buscar compras",
            description = "Lista compras con filtros opcionales y paginación. Solo accesible para **ADMINISTRATOR** y **SUPERVISOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de compras",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Page<PurchaseDto>> searchPurchases(
            @Parameter(description = "Filtrar por ID del proveedor", example = "2")
            @RequestParam(required = false) Long supplierId,
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
        Page<PurchaseDto> purchases = purchaseFacade.searchPurchases(supplierId, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(purchases);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Obtener compra por ID",
            description = "Retorna el detalle completo de una compra incluyendo todos sus ítems y el proveedor."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Compra encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PurchaseDto.class))),
            @ApiResponse(responseCode = "404", description = "Compra no encontrada", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<PurchaseDto> getPurchaseById(
            @Parameter(description = "ID de la compra", example = "1", required = true)
            @PathVariable Long id) {
        return purchaseFacade.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
