package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.TransitionBillingStatusRequest;
import com.sciencebot.pos.billing.dto.UpdateBillingCredentialsCommand;
import com.sciencebot.pos.billing.internal.controllers.doc.StoreBillingConfigApiDoc;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Habilitación de la facturación electrónica por local: credenciales, entorno y etapa del
 * proceso. Reservado al SUPER_ADMIN (la plataforma es quien recibe las credenciales de Factus
 * y las reparte por local; ver {@code backend/docs/modules/billing/spec.md}).
 */
@RestController
public class StoreBillingConfigController implements StoreBillingConfigApiDoc {

    private final StoreBillingConfigService service;

    public StoreBillingConfigController(StoreBillingConfigService service) {
        this.service = service;
    }

    @Override
    @GetMapping("/api/v1/backoffice/billing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<BillingActivationDto>> listByStatus(@RequestParam BillingActivationStatus status) {
        return ResponseEntity.ok(service.listByStatus(status));
    }

    @Override
    @GetMapping("/api/v1/backoffice/stores/{storeId}/billing")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<BillingActivationDto> get(@PathVariable Long storeId) {
        return ResponseEntity.ok(service.get(storeId));
    }

    @Override
    @PutMapping("/api/v1/backoffice/stores/{storeId}/billing/credentials")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<BillingActivationDto> updateCredentials(
            @PathVariable Long storeId,
            @RequestParam BillingEnvironment environment,
            @Valid @RequestBody UpdateBillingCredentialsCommand command,
            Authentication authentication) {
        return ResponseEntity.ok(service.updateCredentials(storeId, environment, command, authentication.getName()));
    }

    @Override
    @PostMapping("/api/v1/backoffice/stores/{storeId}/billing/test-connection")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<BillingConnectionTestResult> testConnection(
            @PathVariable Long storeId,
            @RequestParam BillingEnvironment environment) {
        return ResponseEntity.ok(service.testConnection(storeId, environment));
    }

    @Override
    @PatchMapping("/api/v1/backoffice/stores/{storeId}/billing/environment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<BillingActivationDto> switchEnvironment(
            @PathVariable Long storeId,
            @RequestParam BillingEnvironment environment,
            Authentication authentication) {
        return ResponseEntity.ok(service.switchEnvironment(storeId, environment, authentication.getName()));
    }

    @Override
    @PostMapping("/api/v1/backoffice/stores/{storeId}/billing/transition")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<BillingActivationDto> transition(
            @PathVariable Long storeId,
            @Valid @RequestBody TransitionBillingStatusRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(service.transition(storeId, request.status(), request.reason(), authentication.getName()));
    }
}
