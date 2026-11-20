package com.sciencebot.pos.businesses.internal.controllers;

import com.sciencebot.pos.businesses.AddBranchCommand;
import com.sciencebot.pos.businesses.BusinessDto;
import com.sciencebot.pos.businesses.ChangeBillingModeCommand;
import com.sciencebot.pos.businesses.BusinessFacade;
import com.sciencebot.pos.businesses.CreateBusinessCommand;
import com.sciencebot.pos.businesses.UpdateBusinessCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/backoffice/businesses")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Back Office - Negocios", description = "Negocios con varias sucursales (SUPER_ADMIN)")
public class BusinessBackofficeController {

    private final BusinessFacade businessFacade;

    public BusinessBackofficeController(BusinessFacade businessFacade) {
        this.businessFacade = businessFacade;
    }

    @PostMapping
    @Operation(summary = "Crear un negocio alrededor de un local existente y su administrador")
    public ResponseEntity<BusinessDto> create(@Valid @RequestBody CreateBusinessCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(businessFacade.create(command));
    }

    @GetMapping
    @Operation(summary = "Listar negocios, con filtro opcional por nombre")
    public ResponseEntity<Page<BusinessDto>> search(@RequestParam(required = false) String q, Pageable pageable) {
        return ResponseEntity.ok(businessFacade.search(q, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un negocio con sus sucursales")
    public ResponseEntity<BusinessDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(businessFacade.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cambiar el nombre o el máximo de sucursales")
    public ResponseEntity<BusinessDto> update(@PathVariable Long id, @Valid @RequestBody UpdateBusinessCommand command) {
        return ResponseEntity.ok(businessFacade.update(id, command));
    }

    @PatchMapping("/{id}/billing-mode")
    @Operation(summary = "Cambiar el modo de facturación (NIT compartido o distinto); bloqueado con facturas validadas")
    public ResponseEntity<BusinessDto> changeBillingMode(@PathVariable Long id,
                                                         @Valid @RequestBody ChangeBillingModeCommand command) {
        return ResponseEntity.ok(businessFacade.changeBillingMode(id, command.billingMode()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Disolver el negocio: los locales quedan independientes (no se elimina ninguno)")
    public ResponseEntity<Void> dissolve(@PathVariable Long id) {
        businessFacade.dissolve(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stores")
    @Operation(summary = "Agregar un local existente como sucursal")
    public ResponseEntity<BusinessDto> addBranch(@PathVariable Long id, @Valid @RequestBody AddBranchCommand command) {
        return ResponseEntity.ok(businessFacade.addBranch(id, command.storeId()));
    }

    @DeleteMapping("/{id}/stores/{storeId}")
    @Operation(summary = "Sacar una sucursal del negocio (no elimina el local)")
    public ResponseEntity<BusinessDto> removeBranch(@PathVariable Long id, @PathVariable Long storeId) {
        return ResponseEntity.ok(businessFacade.removeBranch(id, storeId));
    }
}
