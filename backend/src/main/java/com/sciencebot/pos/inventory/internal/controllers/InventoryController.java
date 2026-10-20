package com.sciencebot.pos.inventory.internal.controllers;

import com.sciencebot.pos.inventory.CreateMovementCommand;
import com.sciencebot.pos.inventory.InventoryMovementDto;
import com.sciencebot.pos.inventory.InventoryFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/inventory/movements")
@Tag(name = "📊 Inventario", description = "Movimientos de inventario: entradas, salidas y ajustes")
public class InventoryController {

    private final InventoryFacade inventoryFacade;

    public InventoryController(InventoryFacade inventoryFacade) {
        this.inventoryFacade = inventoryFacade;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Registrar movimiento de inventario",
            description = """
                    Registra un movimiento manual de inventario para un producto.
                    
                    **Tipos de movimiento (`movementType`):**
                    | Tipo | Descripción |
                    |------|-------------|
                    | `ENTRY` | Entrada de mercancía (aumenta stock) |
                    | `EXIT` | Salida de mercancía (disminuye stock) |
                    | `ADJUSTMENT` | Ajuste de inventario (puede ser positivo o negativo) |
                    | `RETURN` | Devolución de mercancía al proveedor (disminuye stock) |
                    | `DEVOLUCION_VENTA` | Devolución de un cliente (aumenta stock) |

                    > Las ventas generan movimientos de tipo `EXIT` automáticamente.
                    > Las compras generan movimientos de tipo `ENTRY` automáticamente.
                    > Las devoluciones de venta (`/api/v1/sales/{saleId}/returns`) generan movimientos de tipo `DEVOLUCION_VENTA` automáticamente.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Movimiento registrado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = InventoryMovementDto.class))),
            @ApiResponse(responseCode = "400", description = "Stock insuficiente o datos inválidos", content = @Content),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<InventoryMovementDto> registerMovement(@RequestBody CreateMovementCommand command) {
        InventoryMovementDto created = inventoryFacade.registerMovement(
                command.productId(),
                command.movementType(),
                command.quantity(),
                command.reason()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Buscar movimientos de inventario",
            description = """
                    Lista el historial de movimientos con filtros opcionales. Soporta paginación.
                    - `productId` — filtra movimientos de un producto específico
                    - `movementType` — filtra por tipo: `ENTRY`, `EXIT`, `ADJUSTMENT`, `RETURN`
                    - `dateFrom` / `dateTo` — rango de fechas en formato ISO 8601
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de movimientos de inventario",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    public ResponseEntity<Page<InventoryMovementDto>> searchMovements(
            @Parameter(description = "Filtrar por ID de producto", example = "5")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "Tipo de movimiento: ENTRY, EXIT, ADJUSTMENT, RETURN", example = "ENTRY")
            @RequestParam(required = false) String movementType,
            @Parameter(description = "Fecha inicial (ISO 8601)", example = "2025-01-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "Fecha final (ISO 8601)", example = "2025-12-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @Parameter(description = "Número de página (desde 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryMovementDto> movements = inventoryFacade.searchMovements(productId, movementType, dateFrom, dateTo, pageable);
        return ResponseEntity.ok(movements);
    }
}
