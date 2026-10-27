package com.sciencebot.pos.settings.internal.controllers;

import com.sciencebot.pos.settings.BrandingDto;
import com.sciencebot.pos.settings.SettingsDto;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.settings.UpdateSettingsCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "⚙️ Configuración", description = "Parámetros globales del negocio")
public class SettingsController {

    private final SettingsFacade settingsFacade;

    public SettingsController(SettingsFacade settingsFacade) {
        this.settingsFacade = settingsFacade;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Obtener configuración del negocio",
            description = """
                    Retorna los parámetros globales configurados para el negocio:
                    nombre comercial, dirección, teléfono, NIT/RUT, email y logo.
                    
                    Accesible para **ADMINISTRATOR** y **SUPERVISOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuración actual del negocio",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SettingsDto.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<SettingsDto> getSettings() {
        return ResponseEntity.ok(settingsFacade.getSettings());
    }

    @GetMapping("/branding")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Obtener nombre y logo del negocio",
            description = "Solo el nombre comercial y el logo, para mostrarlos en la cabecera. Accesible para cualquier usuario del local."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identidad del negocio",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = BrandingDto.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    public ResponseEntity<BrandingDto> getBranding() {
        SettingsDto settings = settingsFacade.getSettings();
        return ResponseEntity.ok(new BrandingDto(settings.businessName(), settings.logoUrl()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Actualizar configuración del negocio",
            description = """
                    Actualiza los parámetros globales del negocio. Solo para **ADMINISTRATOR**.
                    
                    Estos datos se usan en la generación de facturas electrónicas y reportes.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuración actualizada correctamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SettingsDto.class))),
            @ApiResponse(responseCode = "400", description = "Datos de configuración inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Solo ADMINISTRATOR puede modificar la configuración", content = @Content)
    })
    public ResponseEntity<SettingsDto> updateSettings(@RequestBody UpdateSettingsCommand command) {
        SettingsDto updated = settingsFacade.updateSettings(command);
        return ResponseEntity.ok(updated);
    }
}
