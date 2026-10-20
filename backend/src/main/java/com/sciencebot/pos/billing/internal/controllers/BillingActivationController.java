package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.internal.controllers.doc.BillingActivationApiDoc;
import com.sciencebot.pos.billing.internal.services.StoreBillingConfigService;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Facturación electrónica vista desde el local: su estado actual y la solicitud de activación.
 * Las credenciales las carga el SUPER_ADMIN (ver {@link StoreBillingConfigController}); este
 * controlador nunca las expone ni las recibe.
 */
@RestController
@RequestMapping("/api/v1/billing/activation")
public class BillingActivationController implements BillingActivationApiDoc {

    private final StoreBillingConfigService service;

    public BillingActivationController(StoreBillingConfigService service) {
        this.service = service;
    }

    @Override
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<BillingActivationDto> getStatus() {
        return ResponseEntity.ok(service.get(requireStoreId()));
    }

    @Override
    @PostMapping("/request")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<BillingActivationDto> requestActivation(Authentication authentication) {
        return ResponseEntity.ok(service.requestActivation(requireStoreId(), authentication.getName()));
    }

    private static Long requireStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
