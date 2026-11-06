package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.dto.BillingSetupDto;
import com.sciencebot.pos.billing.dto.DianEnablementRequest;
import com.sciencebot.pos.billing.dto.FiscalProfileRequest;
import com.sciencebot.pos.billing.dto.NumberingImportResultDto;
import com.sciencebot.pos.billing.internal.controllers.doc.BillingSetupApiDoc;
import com.sciencebot.pos.billing.internal.services.BillingSetupService;
import com.sciencebot.pos.config.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Asistente de habilitación de la facturación electrónica del local autenticado. El local sale siempre de la
 * solicitud ({@link TenantContext}); sin local no hay respaldo a otro.
 */
@RestController
@RequestMapping("/api/v1/billing/setup")
public class BillingSetupController implements BillingSetupApiDoc {

    private final BillingSetupService service;

    public BillingSetupController(BillingSetupService service) {
        this.service = service;
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<BillingSetupDto> getSetup() {
        return ResponseEntity.ok(service.getSetup(requireStoreId()));
    }

    @Override
    @PutMapping("/fiscal-profile")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<BillingSetupDto> saveFiscalProfile(@Valid @RequestBody FiscalProfileRequest request,
                                                             Authentication authentication) {
        return ResponseEntity.ok(service.saveFiscalProfile(requireStoreId(), request, authentication.getName()));
    }

    @Override
    @PostMapping("/dian-enablement")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<BillingSetupDto> startDianEnablement(@Valid @RequestBody DianEnablementRequest request,
                                                               Authentication authentication) {
        return ResponseEntity.ok(service.startDianEnablement(requireStoreId(), request.testSetId(),
                authentication.getName()));
    }

    @Override
    @PostMapping("/numbering/import")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<NumberingImportResultDto> importNumbering(Authentication authentication) {
        return ResponseEntity.ok(service.importNumbering(requireStoreId(), authentication.getName()));
    }

    private static Long requireStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
