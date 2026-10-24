package com.sciencebot.pos.backoffice.internal.controllers;

import com.sciencebot.pos.backoffice.BackofficeStatsDto;
import com.sciencebot.pos.backoffice.internal.repositories.BackofficeStatsDao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backoffice/stats")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Back Office - Estadísticas", description = "Indicadores y series agregadas de toda la plataforma (solo lectura)")
public class BackofficeStatsController {

    private final BackofficeStatsDao statsDao;

    public BackofficeStatsController(BackofficeStatsDao statsDao) {
        this.statsDao = statsDao;
    }

    @GetMapping("/overview")
    @Operation(summary = "Panorama general de la plataforma",
            description = "Locales por estado y plan, usuarios por rol, facturación electrónica, locales nuevos y ventas por mes "
                    + "(12 meses) y los locales con más ventas en los últimos 30 días.")
    public ResponseEntity<BackofficeStatsDto> overview() {
        return ResponseEntity.ok(statsDao.overview());
    }
}
