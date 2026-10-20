package com.sciencebot.pos.reports.internal.controllers;

import com.sciencebot.pos.reports.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "📈 Reportes", description = "Reportes de ventas, stock, rentabilidad y compras")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/sales")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Reporte de ventas",
            description = """
                    Genera un resumen de ventas para el período indicado. Incluye:
                    - **totalSold**: monto total vendido
                    - **transactionCount**: número de transacciones realizadas
                    - **salesBySeller**: desglose del monto vendido por cada vendedor
                    
                    Si no se especifican fechas, incluye todas las ventas del sistema. Solo para **ADMINISTRATOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte de ventas generado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SalesReportDto.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Solo ADMINISTRATOR puede acceder a reportes", content = @Content)
    })
    public ResponseEntity<SalesReportDto> getSalesReport(
            @Parameter(description = "Fecha inicial del período (ISO 8601)", example = "2025-01-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "Fecha final del período (ISO 8601)", example = "2025-12-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo
    ) {
        return ResponseEntity.ok(reportService.getSalesReport(dateFrom, dateTo));
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Reporte de productos más vendidos",
            description = """
                    Retorna el ranking de los productos con mayor volumen de ventas en el período.
                    - `limit` controla cuántos productos incluir en el ranking (default: 10)
                    
                    Solo para **ADMINISTRATOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ranking de productos más vendidos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = TopProductDto.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<List<TopProductDto>> getTopProductsReport(
            @Parameter(description = "Fecha inicial del período (ISO 8601)", example = "2025-01-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "Fecha final del período (ISO 8601)", example = "2025-12-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @Parameter(description = "Número de productos a incluir en el ranking", example = "10")
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(reportService.getTopProductsReport(dateFrom, dateTo, limit));
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Reporte de stock",
            description = """
                    Muestra el estado actual del inventario de todos los productos.
                    - `belowMinStock = true` — filtra solo productos con stock por debajo del mínimo (alertas de reabastecimiento)
                    - `belowMinStock = false` — muestra todos los productos
                    
                    Accesible para **ADMINISTRATOR** y **SUPERVISOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte de stock generado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = StockReportDto.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<List<StockReportDto>> getStockReport(
            @Parameter(description = "Si es true, retorna solo productos con stock menor al mínimo", example = "true")
            @RequestParam(required = false) Boolean belowMinStock
    ) {
        return ResponseEntity.ok(reportService.getStockReport(belowMinStock));
    }

    @GetMapping("/profitability")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Reporte de rentabilidad por producto",
            description = """
                    Calcula la ganancia bruta por producto en el período indicado.
                    La ganancia se calcula como: `(precio de venta - costo de compra) × unidades vendidas`.
                    
                    Solo para **ADMINISTRATOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte de rentabilidad generado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = ProfitabilityReportDto.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Solo ADMINISTRATOR puede acceder a reportes", content = @Content)
    })
    public ResponseEntity<List<ProfitabilityReportDto>> getProfitabilityReport(
            @Parameter(description = "Fecha inicial del período (ISO 8601)", example = "2025-01-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "Fecha final del período (ISO 8601)", example = "2025-12-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo
    ) {
        return ResponseEntity.ok(reportService.getProfitabilityReport(dateFrom, dateTo));
    }

    @GetMapping("/purchases")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Reporte de compras",
            description = """
                    Genera un resumen del volumen y monto de compras realizadas en el período.
                    
                    Solo para **ADMINISTRATOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte de compras generado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PurchasesReportDto.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Solo ADMINISTRATOR puede acceder a reportes", content = @Content)
    })
    public ResponseEntity<PurchasesReportDto> getPurchasesReport(
            @Parameter(description = "Fecha inicial del período (ISO 8601)", example = "2025-01-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "Fecha final del período (ISO 8601)", example = "2025-12-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo
    ) {
        return ResponseEntity.ok(reportService.getPurchasesReport(dateFrom, dateTo));
    }

    @GetMapping("/cash-closing")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Cierre de caja",
            description = """
                    Calcula cuánto efectivo debería haber en caja al final de un turno/día: la suma de
                    las ventas en efectivo (`CASH`) del período, desglosada por vendedor para que cada
                    cajero pueda rendir cuentas. También incluye el total vendido por cada método de
                    pago (`totalByPaymentMethod`) como referencia, aunque solo el efectivo requiere
                    conteo físico.

                    El conteo físico de efectivo y la diferencia contra `expectedCash` se calculan en el
                    cliente; este endpoint no persiste ningún cierre.

                    Solo para **ADMINISTRATOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cierre de caja generado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CashClosingReportDto.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Solo ADMINISTRATOR puede acceder a reportes", content = @Content)
    })
    public ResponseEntity<CashClosingReportDto> getCashClosingReport(
            @Parameter(description = "Fecha inicial del período (ISO 8601)", example = "2025-01-01T00:00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @Parameter(description = "Fecha final del período (ISO 8601)", example = "2025-12-31T23:59:59")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo
    ) {
        return ResponseEntity.ok(reportService.getCashClosingReport(dateFrom, dateTo));
    }
}
