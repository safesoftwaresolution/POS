package com.sciencebot.pos.settings.internal.controllers;

import com.sciencebot.pos.settings.CompanyLogoResponseDto;
import com.sciencebot.pos.settings.CompanyLogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/v1/settings/logo", "/api/v1/company/logo", "/api/v1/companies/logo"})
@Tag(name = "⚙️ Configuración / Empresa", description = "Gestión y sincronización del logo de la empresa con Factus")
public class CompanyLogoController {

    private final CompanyLogoService companyLogoService;

    public CompanyLogoController(CompanyLogoService companyLogoService) {
        this.companyLogoService = companyLogoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Actualizar logo de la empresa y sincronizar con Factus",
            description = """
                    Recibe un archivo de imagen en formato PNG, JPG o JPEG.
                    
                    **Validaciones aplicadas:**
                    - Peso máximo: menos de 200 KB.
                    - Dimensiones máximas: iguales o inferiores a 300x300 píxeles.
                    - Formatos permitidos: PNG, JPG o JPEG.
                    
                    **Efectos:**
                    - Actualiza la propiedad `logoUrl` del negocio en la base de datos local.
                    - Envía la imagen mediante multipart/form-data a la API de Factus (`/v2/companies/logo`).
                    
                    Requiere rol **ADMINISTRATOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Logo actualizado y sincronizado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CompanyLogoResponseDto.class))
            ),
            @ApiResponse(responseCode = "400", description = "Archivo inválido (peso, formato o dimensiones incorrectas)", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos de ADMINISTRATOR", content = @Content),
            @ApiResponse(responseCode = "409", description = "Error de sincronización con Factus API", content = @Content)
    })
    public ResponseEntity<CompanyLogoResponseDto> updateLogo(
            @Parameter(description = "Archivo de imagen del logo (PNG, JPG o JPEG, < 200 KB, <= 300x300 px)")
            @RequestParam(value = "file", required = false) MultipartFile fileParam,
            @Parameter(description = "Alias opcional del parámetro de imagen (compatible con Factus)")
            @RequestParam(value = "image", required = false) MultipartFile imageParam
    ) {
        MultipartFile file = (fileParam != null && !fileParam.isEmpty()) ? fileParam : imageParam;

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Debe adjuntar un archivo de imagen en el parámetro 'file' o 'image'.");
        }

        CompanyLogoResponseDto response = companyLogoService.updateLogo(file);
        return ResponseEntity.ok(response);
    }
}
