package com.sciencebot.pos.audit.internal.controllers;

import com.sciencebot.pos.audit.AuditEntryDto;
import com.sciencebot.pos.audit.AuditFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Auditoría", description = "Historial de cambios sobre clientes y otras entidades del local")
public class AuditController {

    private final AuditFacade auditFacade;

    public AuditController(AuditFacade auditFacade) {
        this.auditFacade = auditFacade;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(summary = "Historial de una entidad",
            description = "Cambios registrados sobre una entidad del local (por ejemplo `entityType=CUSTOMER`), "
                    + "del más reciente al más antiguo. Solo ADMINISTRATOR.")
    public ResponseEntity<Page<AuditEntryDto>> history(
            @RequestParam String entityType,
            @RequestParam Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(auditFacade.history(entityType, entityId, PageRequest.of(page, size)));
    }
}
