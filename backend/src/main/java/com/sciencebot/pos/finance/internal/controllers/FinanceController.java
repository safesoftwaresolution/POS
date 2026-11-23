package com.sciencebot.pos.finance.internal.controllers;

import com.sciencebot.pos.finance.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Finanzas del local (base caja). Son datos de dinero: solo el administrador, como los reportes. */
@RestController
@RequestMapping("/api/v1/finance")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@Tag(name = "💰 Finanzas", description = "Estado financiero en base caja, movimientos manuales y costos fijos")
public class FinanceController {

    private static final int MAX_PAGE_SIZE = 100;

    private final FinanceFacade financeFacade;

    public FinanceController(FinanceFacade financeFacade) {
        this.financeFacade = financeFacade;
    }

    @GetMapping("/summary")
    @Operation(summary = "Estado financiero del período",
            description = "Ingresos (ventas, abonos de fiado y otros ingresos), compras, egresos manuales, costos fijos "
                    + "prorrateados y utilidad, con variación frente al período anterior. Sin fechas usa los últimos "
                    + "30 días (máximo 366). Solo **ADMINISTRATOR**.")
    public ResponseEntity<FinanceSummaryDto> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(financeFacade.getSummary(dateFrom, dateTo));
    }

    @GetMapping("/movements")
    @Operation(summary = "Listar movimientos manuales",
            description = "Egresos e ingresos registrados a mano en el período, del más reciente al más antiguo.")
    public ResponseEntity<Page<MovementDto>> listMovements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(financeFacade.listMovements(dateFrom, dateTo,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE))));
    }

    @PostMapping("/movements")
    @Operation(summary = "Registrar un egreso o un ingreso manual")
    public ResponseEntity<MovementDto> createMovement(@Valid @RequestBody SaveMovementCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(financeFacade.createMovement(command));
    }

    @PutMapping("/movements/{id}")
    @Operation(summary = "Editar un movimiento manual")
    public ResponseEntity<MovementDto> updateMovement(@PathVariable Long id, @Valid @RequestBody SaveMovementCommand command) {
        return ResponseEntity.ok(financeFacade.updateMovement(id, command));
    }

    @DeleteMapping("/movements/{id}")
    @Operation(summary = "Eliminar un movimiento manual")
    public ResponseEntity<Void> deleteMovement(@PathVariable Long id) {
        financeFacade.deleteMovement(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fixed-costs")
    @Operation(summary = "Listar costos fijos mensuales")
    public ResponseEntity<List<FixedCostDto>> listFixedCosts() {
        return ResponseEntity.ok(financeFacade.listFixedCosts());
    }

    @PostMapping("/fixed-costs")
    @Operation(summary = "Crear un costo fijo mensual")
    public ResponseEntity<FixedCostDto> createFixedCost(@Valid @RequestBody SaveFixedCostCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(financeFacade.createFixedCost(command));
    }

    @PutMapping("/fixed-costs/{id}")
    @Operation(summary = "Editar o activar/desactivar un costo fijo")
    public ResponseEntity<FixedCostDto> updateFixedCost(@PathVariable Long id, @Valid @RequestBody SaveFixedCostCommand command) {
        return ResponseEntity.ok(financeFacade.updateFixedCost(id, command));
    }

    @DeleteMapping("/fixed-costs/{id}")
    @Operation(summary = "Eliminar un costo fijo")
    public ResponseEntity<Void> deleteFixedCost(@PathVariable Long id) {
        financeFacade.deleteFixedCost(id);
        return ResponseEntity.noContent().build();
    }
}
