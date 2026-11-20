package com.sciencebot.pos.businesses.internal.controllers;

import com.sciencebot.pos.businesses.BranchDto;
import com.sciencebot.pos.businesses.BranchesStatsDto;
import com.sciencebot.pos.businesses.BusinessStatsDto;
import com.sciencebot.pos.businesses.internal.services.BusinessStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import com.sciencebot.pos.businesses.BusinessFacade;
import com.sciencebot.pos.stores.CreateStoreCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.sciencebot.pos.businesses.MyBusinessDto;
import com.sciencebot.pos.config.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business")
@PreAuthorize("hasRole('ADMINISTRATOR')")
@Tag(name = "Negocio", description = "Negocio con varias sucursales del dueño (ADMINISTRATOR)")
public class BusinessOwnerController {

    private final BusinessFacade businessFacade;

    private final BusinessStatsService statsService;

    public BusinessOwnerController(BusinessFacade businessFacade, BusinessStatsService statsService) {
        this.businessFacade = businessFacade;
        this.statsService = statsService;
    }

    @GetMapping("/stats/overview")
    @Operation(summary = "Totales de todas las sucursales juntas (ventas, devoluciones, métodos de pago y serie diaria)")
    public ResponseEntity<BusinessStatsDto> overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return ResponseEntity.ok(statsService.overview(authentication.getName(), from, to));
    }

    @GetMapping("/stats/branches")
    @Operation(summary = "Las mismas cifras por sucursal, ordenadas por venta neta")
    public ResponseEntity<BranchesStatsDto> branches(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        return ResponseEntity.ok(statsService.branches(authentication.getName(), from, to));
    }

    @PostMapping("/branches")
    @Operation(summary = "Crear una sucursal nueva; queda pendiente de revisión del SUPER_ADMIN")
    public ResponseEntity<BranchDto> createBranch(@Valid @RequestBody CreateStoreCommand command,
                                                  Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(businessFacade.createBranch(authentication.getName(), command));
    }

    @GetMapping("/me")
    @Operation(summary = "Negocio del dueño con sus sucursales; 404 si el local no pertenece a un negocio")
    public ResponseEntity<MyBusinessDto> me(Authentication authentication) {
        // El negocio sale del usuario autenticado, nunca de un id enviado por el cliente.
        return businessFacade.findByOwnerUsername(authentication.getName())
                .map(business -> ResponseEntity.ok(new MyBusinessDto(business, TenantContext.getStoreId())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
