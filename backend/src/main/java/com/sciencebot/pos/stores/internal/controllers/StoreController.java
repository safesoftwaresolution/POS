package com.sciencebot.pos.stores.internal.controllers;

import com.sciencebot.pos.stores.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Back Office - Locales", description = "Gestion de locales del SaaS (SUPER_ADMIN)")
public class StoreController {

    private final StoreFacade storeFacade;

    public StoreController(StoreFacade storeFacade) {
        this.storeFacade = storeFacade;
    }

    @GetMapping("/api/v1/backoffice/metrics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Metricas SaaS del Back Office")
    public ResponseEntity<StoreMetricsDto> getMetrics() {
        return ResponseEntity.ok(storeFacade.getMetrics());
    }

    @GetMapping("/api/v1/backoffice/stores")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar/buscar locales con filtros")
    public ResponseEntity<Page<StoreDto>> searchStores(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            Pageable pageable) {
        return ResponseEntity.ok(storeFacade.searchStores(status, q, pageable));
    }

    @GetMapping("/api/v1/backoffice/stores/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Obtener un local por ID")
    public ResponseEntity<StoreDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(storeFacade.getById(id));
    }

    @PostMapping("/api/v1/backoffice/stores")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear un nuevo local")
    public ResponseEntity<StoreDto> createStore(@RequestBody CreateStoreCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeFacade.createStore(command));
    }

    @PutMapping("/api/v1/backoffice/stores/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar datos de un local")
    public ResponseEntity<StoreDto> updateStore(@PathVariable Long id, @RequestBody UpdateStoreCommand command) {
        return ResponseEntity.ok(storeFacade.updateStore(id, command));
    }

    @PatchMapping("/api/v1/backoffice/stores/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Cambiar estado del local (ACTIVE, INACTIVE, SUSPENDED, PENDING_VERIFICATION)")
    public ResponseEntity<StoreDto> changeStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(storeFacade.changeStatus(id, body.get("status")));
    }

    @PatchMapping("/api/v1/backoffice/stores/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Rechazar un local pendiente (requiere motivo)")
    public ResponseEntity<StoreDto> rejectStore(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(storeFacade.rejectStore(id, body.get("reason")));
    }

    @PatchMapping("/api/v1/backoffice/stores/{id}/verify-email")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Marcar correo del local como verificado")
    public ResponseEntity<StoreDto> verifyEmail(@PathVariable Long id) {
        return ResponseEntity.ok(storeFacade.verifyEmail(id));
    }

    @GetMapping("/api/v1/backoffice/stores/{storeId}/documents")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar documentos enviados por un local")
    public ResponseEntity<List<StoreDocumentDto>> getDocuments(@PathVariable Long storeId) {
        return ResponseEntity.ok(storeFacade.getDocuments(storeId));
    }

    @PatchMapping("/api/v1/backoffice/stores/{storeId}/documents/{docId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Aprobar o rechazar un documento del local")
    public ResponseEntity<StoreDocumentDto> reviewDocument(
            @PathVariable Long storeId,
            @PathVariable Long docId,
            @RequestBody ReviewDocumentCommand command) {
        return ResponseEntity.ok(storeFacade.reviewDocument(storeId, docId, command));
    }

    @GetMapping("/api/v1/backoffice/store-categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar categorias de locales")
    public ResponseEntity<List<StoreCategoryDto>> listCategories() {
        return ResponseEntity.ok(storeFacade.listCategories());
    }

    @PostMapping("/api/v1/backoffice/store-categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear categoria de local")
    public ResponseEntity<StoreCategoryDto> createCategory(@RequestBody CreateStoreCategoryCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeFacade.createCategory(command));
    }

    @PutMapping("/api/v1/backoffice/store-categories/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar categoria de local")
    public ResponseEntity<StoreCategoryDto> updateCategory(@PathVariable Long id, @RequestBody CreateStoreCategoryCommand command) {
        return ResponseEntity.ok(storeFacade.updateCategory(id, command));
    }

    @DeleteMapping("/api/v1/backoffice/store-categories/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Desactivar categoria de local")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        storeFacade.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}