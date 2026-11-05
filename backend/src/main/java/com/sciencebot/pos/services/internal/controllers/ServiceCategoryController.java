package com.sciencebot.pos.services.internal.controllers;

import com.sciencebot.pos.services.CreateServiceCategoryCommand;
import com.sciencebot.pos.services.ServiceCategoryDto;
import com.sciencebot.pos.services.ServiceCategoryFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Categorías de servicios", description = "Catálogo global de categorías de servicios, ofrecido por tipo de local")
public class ServiceCategoryController {

    private final ServiceCategoryFacade categoryFacade;

    public ServiceCategoryController(ServiceCategoryFacade categoryFacade) {
        this.categoryFacade = categoryFacade;
    }

    @GetMapping("/api/v1/service-categories")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Categorías de servicios disponibles para el local actual")
    public ResponseEntity<List<ServiceCategoryDto>> listForStore() {
        return ResponseEntity.ok(categoryFacade.listForCurrentStore());
    }

    @GetMapping("/api/v1/backoffice/service-categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar todas las categorías de servicios")
    public ResponseEntity<List<ServiceCategoryDto>> listAll() {
        return ResponseEntity.ok(categoryFacade.listAll());
    }

    @PostMapping("/api/v1/backoffice/service-categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear categoría de servicios")
    public ResponseEntity<ServiceCategoryDto> create(@RequestBody CreateServiceCategoryCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryFacade.create(command));
    }

    @PutMapping("/api/v1/backoffice/service-categories/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar categoría de servicios")
    public ResponseEntity<ServiceCategoryDto> update(@PathVariable Long id, @RequestBody CreateServiceCategoryCommand command) {
        return ResponseEntity.ok(categoryFacade.update(id, command));
    }

    @DeleteMapping("/api/v1/backoffice/service-categories/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Desactivar categoría de servicios")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        categoryFacade.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
