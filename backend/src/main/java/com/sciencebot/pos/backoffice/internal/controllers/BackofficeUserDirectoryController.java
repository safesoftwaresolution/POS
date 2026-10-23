package com.sciencebot.pos.backoffice.internal.controllers;

import com.sciencebot.pos.backoffice.BackofficeUserDto;
import com.sciencebot.pos.backoffice.StorePlanUsageDto;
import com.sciencebot.pos.backoffice.StoreWithoutOwnerDto;
import com.sciencebot.pos.backoffice.internal.repositories.UserDirectoryDao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/backoffice")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Back Office - Directorio de usuarios",
        description = "Solo lectura: usuarios de la plataforma con su local y rol, locales sin dueño y candidatos a asignar")
public class BackofficeUserDirectoryController {

    private static final Set<String> ROLES = Set.of("SUPER_ADMIN", "ADMINISTRATOR", "SUPERVISOR", "SELLER");

    private final UserDirectoryDao userDirectoryDao;

    public BackofficeUserDirectoryController(UserDirectoryDao userDirectoryDao) {
        this.userDirectoryDao = userDirectoryDao;
    }

    @GetMapping("/users")
    @Operation(summary = "Directorio de usuarios con su local y rol",
            description = "Filtros opcionales: role, storeId, q (nombre, usuario, correo o local) y unassigned=true (ADMINISTRATOR sin local).")
    public ResponseEntity<Page<BackofficeUserDto>> search(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean unassigned,
            Pageable pageable) {
        String roleParam = (role == null || role.isBlank() || role.equalsIgnoreCase("ALL")) ? null : role.toUpperCase();
        if (roleParam != null && !ROLES.contains(roleParam)) {
            throw new IllegalArgumentException("Rol invalido: " + role);
        }
        String qParam = (q == null || q.isBlank()) ? null : q.trim();
        return ResponseEntity.ok(userDirectoryDao.search(roleParam, storeId, qParam, unassigned, pageable));
    }

    @GetMapping("/users/assignable-owners")
    @Operation(summary = "ADMINISTRATOR verificados que aun no tienen local (candidatos a asignar)")
    public ResponseEntity<List<BackofficeUserDto>> assignableOwners() {
        return ResponseEntity.ok(userDirectoryDao.findAssignableOwners());
    }

    @GetMapping("/store-plan-usage")
    @Operation(summary = "Plan de cada local y usuarios activos que usa frente a su tope")
    public ResponseEntity<List<StorePlanUsageDto>> storePlanUsage() {
        return ResponseEntity.ok(userDirectoryDao.findStorePlanUsage());
    }

    @GetMapping("/stores-without-owner")
    @Operation(summary = "Locales sin ningun administrador activo")
    public ResponseEntity<List<StoreWithoutOwnerDto>> storesWithoutOwner() {
        return ResponseEntity.ok(userDirectoryDao.findStoresWithoutOwner());
    }
}
