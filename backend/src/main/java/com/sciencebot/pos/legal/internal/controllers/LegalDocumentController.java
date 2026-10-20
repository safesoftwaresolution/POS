package com.sciencebot.pos.legal.internal.controllers;

import com.sciencebot.pos.legal.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Textos Legales", description = "CMS de politicas de privacidad, terminos, cookies y mas")
public class LegalDocumentController {

    private final LegalDocumentFacade facade;

    public LegalDocumentController(LegalDocumentFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/api/v1/legal/{slug}")
    @Operation(summary = "Obtener texto legal publicado (publico)")
    public ResponseEntity<LegalDocumentDto> getPublished(@PathVariable String slug) {
        return ResponseEntity.ok(facade.getPublished(slug));
    }

    @GetMapping("/api/v1/backoffice/legal-documents")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar todos los documentos legales")
    public ResponseEntity<List<LegalDocumentDto>> listAll() {
        return ResponseEntity.ok(facade.listAll());
    }

    @GetMapping("/api/v1/backoffice/legal-documents/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Obtener documento legal por ID")
    public ResponseEntity<LegalDocumentDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(facade.getById(id));
    }

    @PostMapping("/api/v1/backoffice/legal-documents")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear documento legal")
    public ResponseEntity<LegalDocumentDto> create(@RequestBody SaveLegalDocumentCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(facade.create(command));
    }

    @PutMapping("/api/v1/backoffice/legal-documents/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar documento legal")
    public ResponseEntity<LegalDocumentDto> update(@PathVariable Long id, @RequestBody SaveLegalDocumentCommand command) {
        return ResponseEntity.ok(facade.update(id, command));
    }

    @DeleteMapping("/api/v1/backoffice/legal-documents/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Eliminar documento legal (solo si no esta publicado)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        facade.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/v1/backoffice/legal-documents/{id}/publish")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Publicar o despublicar un documento legal")
    public ResponseEntity<LegalDocumentDto> togglePublish(@PathVariable Long id) {
        return ResponseEntity.ok(facade.togglePublish(id));
    }
}