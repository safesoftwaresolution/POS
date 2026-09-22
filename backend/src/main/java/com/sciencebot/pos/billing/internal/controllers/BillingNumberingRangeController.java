package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.NumberingRangeDto;
import com.sciencebot.pos.billing.UpdateNumberingRangeCurrentRequest;
import com.sciencebot.pos.billing.internal.services.NumberingRangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing/numbering-ranges")
@Tag(name = "📄 Facturación Electrónica - Rangos de Numeración", description = "Administración y sincronización de resoluciones y rangos DIAN en Factus")
public class BillingNumberingRangeController {

    private final NumberingRangeService numberingRangeService;

    public BillingNumberingRangeController(NumberingRangeService numberingRangeService) {
        this.numberingRangeService = numberingRangeService;
    }

    @GetMapping("/dian")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Consultar rangos de numeración asociados ante la DIAN",
            description = "Consulta en tiempo real ante la DIAN los rangos y resoluciones autorizados y enlazados con el software."
    )
    public ResponseEntity<List<NumberingRangeDto>> getDianNumberingRanges() {
        return ResponseEntity.ok(numberingRangeService.queryDianNumberingRanges());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Listar rangos de numeración registrados en Factus",
            description = "Lista todos los rangos de numeración registrados y activos en Factus."
    )
    public ResponseEntity<List<NumberingRangeDto>> listNumberingRanges() {
        return ResponseEntity.ok(numberingRangeService.listNumberingRanges());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Consultar detalle de un rango de numeración",
            description = "Obtiene los datos detallados de un rango de numeración por su ID."
    )
    public ResponseEntity<NumberingRangeDto> getNumberingRange(
            @Parameter(description = "ID del rango de numeración", example = "14", required = true)
            @PathVariable Long id) {
        NumberingRangeDto dto = numberingRangeService.getNumberingRange(id);
        if (dto != null) {
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Registrar y activar un nuevo rango de numeración en Factus",
            description = "Registra una resolución DIAN en Factus para habilitarla en la emisión de facturas."
    )
    public ResponseEntity<NumberingRangeDto> createNumberingRange(
            @Valid @RequestBody CreateNumberingRangeRequest request) {
        NumberingRangeDto created = numberingRangeService.createNumberingRange(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Eliminar rango de numeración en Factus",
            description = "Elimina un rango de numeración. Si ya tuvo facturas, Factus lo deja marcado como eliminado en lugar de borrarlo."
    )
    public ResponseEntity<Void> deleteNumberingRange(
            @Parameter(description = "ID del rango de numeración a eliminar", example = "14", required = true)
            @PathVariable Long id) {
        numberingRangeService.deleteNumberingRange(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Cambiar estado (activo/inactivo) de un rango de numeración",
            description = "Alterna el estado de activación de un rango de numeración en Factus y devuelve el rango actualizado."
    )
    public ResponseEntity<NumberingRangeDto> toggleNumberingRangeStatus(
            @Parameter(description = "ID del rango de numeración", example = "14", required = true)
            @PathVariable Long id) {
        return ResponseEntity.ok(numberingRangeService.toggleNumberingRangeStatus(id));
    }

    @PatchMapping("/{id}/current")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Actualizar el consecutivo actual de un rango de numeración",
            description = "Cambia el próximo número a usar. Útil cuando el local ya venía facturando con otro software."
    )
    public ResponseEntity<NumberingRangeDto> updateNumberingRangeCurrent(
            @Parameter(description = "ID del rango de numeración", example = "14", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateNumberingRangeCurrentRequest request) {
        return ResponseEntity.ok(numberingRangeService.updateNumberingRangeCurrent(id, request.current()));
    }
}
