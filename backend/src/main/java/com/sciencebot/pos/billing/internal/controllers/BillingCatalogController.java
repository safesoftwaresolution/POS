package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.dto.DivipolaCatalogDto;
import com.sciencebot.pos.billing.internal.controllers.doc.BillingCatalogApiDoc;
import com.sciencebot.pos.billing.internal.services.DivipolaCatalog;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Catálogos de referencia de la facturación electrónica (datos públicos, iguales para todos los locales). */
@RestController
public class BillingCatalogController implements BillingCatalogApiDoc {

    private static final CacheControl ONE_DAY = CacheControl.maxAge(Duration.ofDays(1));

    private final DivipolaCatalog divipola;

    public BillingCatalogController(DivipolaCatalog divipola) {
        this.divipola = divipola;
    }

    @Override
    @GetMapping("/api/v1/billing/catalogs/divipola")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<DivipolaCatalogDto> getDivipola() {
        return ResponseEntity.ok().cacheControl(ONE_DAY).body(divipola.get());
    }
}
