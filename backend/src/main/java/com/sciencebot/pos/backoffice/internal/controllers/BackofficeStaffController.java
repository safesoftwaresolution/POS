package com.sciencebot.pos.backoffice.internal.controllers;

import com.sciencebot.pos.backoffice.*;
import com.sciencebot.pos.users.ChangePasswordCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/backoffice/staff")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Back Office - Operadores", description = "Gestion de cuentas SUPER_ADMIN de la plataforma")
public class BackofficeStaffController {

    private final BackofficeStaffFacade staffFacade;

    public BackofficeStaffController(BackofficeStaffFacade staffFacade) {
        this.staffFacade = staffFacade;
    }

    @GetMapping
    @Operation(summary = "Listar operadores del backoffice")
    public ResponseEntity<Page<BackofficeStaffDto>> listStaff(Pageable pageable) {
        return ResponseEntity.ok(staffFacade.listStaff(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un operador por ID")
    public ResponseEntity<BackofficeStaffDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(staffFacade.getById(id));
    }

    @PostMapping
    @Operation(summary = "Crear un nuevo operador SUPER_ADMIN")
    public ResponseEntity<BackofficeStaffDto> createStaff(@RequestBody CreateBackofficeStaffCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffFacade.createStaff(command));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar nombre/correo de un operador")
    public ResponseEntity<BackofficeStaffDto> updateStaff(@PathVariable Long id, @RequestBody UpdateBackofficeStaffCommand command) {
        return ResponseEntity.ok(staffFacade.updateStaff(id, command));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activar o desactivar un operador (no permite auto-desactivacion)")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) {
            throw new IllegalArgumentException("El campo 'active' es requerido");
        }
        staffFacade.changeStatus(id, active);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    @Operation(summary = "Restablecer la contrasena de un operador")
    public ResponseEntity<Void> changePassword(@PathVariable Long id, @RequestBody ChangePasswordCommand command) {
        staffFacade.changePassword(id, command);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactivar (baja logica) un operador (no permite auto-eliminacion)")
    public ResponseEntity<Void> deleteStaff(@PathVariable Long id) {
        staffFacade.deleteStaff(id);
        return ResponseEntity.noContent().build();
    }
}
