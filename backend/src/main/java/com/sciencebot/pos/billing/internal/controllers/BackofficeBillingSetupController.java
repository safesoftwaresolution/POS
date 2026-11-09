package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.dto.BillingSetupDto;
import com.sciencebot.pos.billing.internal.controllers.doc.BackofficeBillingSetupApiDoc;
import com.sciencebot.pos.billing.internal.services.BillingSetupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seguimiento del asistente de habilitación de un local desde el back office (solo lectura). El local sale de la
 * ruta, nunca del contexto de la solicitud: el SUPER_ADMIN no tiene local.
 */
@RestController
public class BackofficeBillingSetupController implements BackofficeBillingSetupApiDoc {

    private final BillingSetupService service;

    public BackofficeBillingSetupController(BillingSetupService service) {
        this.service = service;
    }

    @Override
    @GetMapping("/api/v1/backoffice/stores/{storeId}/billing/setup")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<BillingSetupDto> getStoreSetup(@PathVariable Long storeId) {
        return ResponseEntity.ok(service.getSetup(storeId));
    }
}
