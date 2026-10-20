package com.sciencebot.pos.backoffice.internal.controllers;

import com.sciencebot.pos.backoffice.OnboardingStatsDto;
import com.sciencebot.pos.backoffice.internal.repositories.OnboardingStatsDao;
import com.sciencebot.pos.users.UserDto;
import com.sciencebot.pos.users.UserFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backoffice/onboarding")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Back Office - Pendientes", description = "Usuarios y locales pendientes de aprobacion, y estadisticas del embudo de registro")
public class BackofficeOnboardingController {

    private final UserFacade userFacade;
    private final OnboardingStatsDao onboardingStatsDao;

    public BackofficeOnboardingController(UserFacade userFacade, OnboardingStatsDao onboardingStatsDao) {
        this.userFacade = userFacade;
        this.onboardingStatsDao = onboardingStatsDao;
    }

    @GetMapping("/pending-users")
    @Operation(summary = "Listar usuarios pendientes de verificar su correo")
    public ResponseEntity<Page<UserDto>> listPendingUsers(Pageable pageable) {
        return ResponseEntity.ok(userFacade.listPendingVerificationUsers(pageable));
    }

    @GetMapping("/stats")
    @Operation(summary = "Estadisticas del embudo de auto-registro")
    public ResponseEntity<OnboardingStatsDto> getStats() {
        return ResponseEntity.ok(onboardingStatsDao.getStats());
    }
}
