package com.sciencebot.pos.support.internal.controllers;

import com.sciencebot.pos.support.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Soporte y PQRs", description = "Radicacion de PQRs, reportes de bugs y gestion (Back Office)")
public class SupportTicketController {

    private final SupportTicketFacade facade;

    public SupportTicketController(SupportTicketFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/api/v1/support/tickets")
    @Operation(summary = "Radicar PQR o reporte de bug (publico)")
    public ResponseEntity<SupportTicketDto> create(@RequestBody CreateTicketCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facade.create(command));
    }

    @GetMapping("/api/v1/support/tickets/track/{ticketNumber}")
    @Operation(summary = "Consultar estado del ticket por numero de radicado (publico)")
    public ResponseEntity<SupportTicketDto> track(@PathVariable String ticketNumber) {
        return ResponseEntity.ok(facade.trackByNumber(ticketNumber));
    }

    @GetMapping("/api/v1/backoffice/support/tickets")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar tickets con filtros")
    public ResponseEntity<Page<SupportTicketDto>> search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(facade.search(type, status, priority, storeId, q, pageable));
    }

    @GetMapping("/api/v1/backoffice/support/tickets/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Ver detalle de un ticket")
    public ResponseEntity<SupportTicketDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(facade.getById(id));
    }

    @PatchMapping("/api/v1/backoffice/support/tickets/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar estado, prioridad o notas de resolucion del ticket")
    public ResponseEntity<SupportTicketDto> update(@PathVariable Long id, @RequestBody UpdateTicketCommand command) {
        return ResponseEntity.ok(facade.update(id, command));
    }

    @GetMapping("/api/v1/backoffice/support/metrics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Metricas del soporte (abiertos, bugs criticos, etc.)")
    public ResponseEntity<SupportMetricsDto> getMetrics() {
        return ResponseEntity.ok(facade.getMetrics());
    }
}