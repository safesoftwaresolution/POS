package com.sciencebot.pos.services.internal.controllers;

import com.sciencebot.pos.services.CreateServiceCommand;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.services.UpdateServiceCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/services")
@Tag(name = "💆 Servicios", description = "Catálogo de servicios que se cobran y se facturan (sin inventario)")
public class ServiceController {

    private final ServiceFacade serviceFacade;

    public ServiceController(ServiceFacade serviceFacade) {
        this.serviceFacade = serviceFacade;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Buscar servicios",
            description = "Lista los servicios del local con paginación. `search` busca por nombre o código (parcial).")
    public ResponseEntity<Page<ServiceDto>> searchServices(
            @Parameter(description = "Texto de búsqueda (nombre o código)", example = "masaje")
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(serviceFacade.searchServices(search, categoryId, active, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Obtener servicio por ID")
    public ResponseEntity<ServiceDto> getServiceById(@PathVariable Long id) {
        return serviceFacade.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(summary = "Crear servicio",
            description = "Registra un servicio en el catálogo. Requiere rol **ADMINISTRATOR** o **SUPERVISOR**.")
    public ResponseEntity<ServiceDto> createService(@RequestBody CreateServiceCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceFacade.createService(command));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(summary = "Actualizar servicio",
            description = "Actualiza los campos enviados. Las ventas ya emitidas conservan el precio y nombre con que se vendieron.")
    public ResponseEntity<ServiceDto> updateService(@PathVariable Long id, @RequestBody UpdateServiceCommand command) {
        return ResponseEntity.ok(serviceFacade.updateService(id, command));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(summary = "Cambiar estado del servicio",
            description = "Activa o desactiva el servicio. Cuerpo: `{ \"active\": true }`. Un servicio inactivo no se puede vender.")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> statusMap) {
        Boolean active = statusMap.get("active");
        if (active == null) {
            throw new IllegalArgumentException("El campo 'active' es requerido");
        }
        serviceFacade.changeStatus(id, active);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Eliminar servicio",
            description = "Baja lógica: deja el servicio inactivo. Solo **ADMINISTRATOR**.")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        serviceFacade.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
