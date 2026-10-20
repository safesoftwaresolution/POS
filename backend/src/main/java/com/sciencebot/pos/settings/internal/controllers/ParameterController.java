package com.sciencebot.pos.settings.internal.controllers;

import com.sciencebot.pos.settings.*;
import com.sciencebot.pos.settings.internal.services.ParameterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings/parameters")
@Tag(name = "🧩 Parámetros y Catálogos", description = "Motor dinámico de catálogos configurables (métodos de pago, motivos de devolución, zonas de entrega, etc.)")
public class ParameterController {

    private final ParameterService parameterService;

    public ParameterController(ParameterService parameterService) {
        this.parameterService = parameterService;
    }

    @GetMapping("/topics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Listar temas", description = "Retorna todos los temas de parámetros ordenados por nombre.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de temas",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ParameterTopicDto.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<List<ParameterTopicDto>> getTopics() {
        return ResponseEntity.ok(parameterService.getAllTopics());
    }

    @PostMapping("/topics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear tema", description = "Crea un nuevo tema de parámetros. Solo **SUPER_ADMIN** (los catálogos son globales para toda la plataforma).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tema creado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ParameterTopicDto.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe un tema con ese código", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<ParameterTopicDto> createTopic(@RequestBody CreateParameterTopicCommand command) {
        ParameterTopicDto created = parameterService.createTopic(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/topics/{code}/values")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Listar valores de un tema",
            description = "Retorna los valores del tema indicado (incluye inactivos) ordenados por `sortOrder`."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de valores",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ParameterValueDto.class))),
            @ApiResponse(responseCode = "404", description = "Tema no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    public ResponseEntity<List<ParameterValueDto>> getValues(
            @Parameter(description = "Código del tema", example = "PAYMENT_METHODS", required = true)
            @PathVariable String code) {
        return ResponseEntity.ok(parameterService.getValuesByTopic(code));
    }

    @PostMapping("/topics/{code}/values")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Agregar valor", description = "Agrega un valor al tema indicado. Solo **SUPER_ADMIN**.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Valor agregado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ParameterValueDto.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
            @ApiResponse(responseCode = "404", description = "Tema no encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Ya existe un valor con ese código en el tema", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<ParameterValueDto> addValue(
            @Parameter(description = "Código del tema", example = "PAYMENT_METHODS", required = true)
            @PathVariable String code,
            @RequestBody CreateParameterValueCommand command) {
        ParameterValueDto created = parameterService.addValue(code, command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/values/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Actualizar valor",
            description = "Actualiza etiqueta, valor extra, orden o estado de un valor. Los campos nulos no se modifican. Solo **SUPER_ADMIN**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Valor actualizado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ParameterValueDto.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
            @ApiResponse(responseCode = "404", description = "Valor no encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<ParameterValueDto> updateValue(
            @Parameter(description = "ID del valor", example = "10", required = true)
            @PathVariable Long id,
            @RequestBody UpdateParameterValueCommand command) {
        return ResponseEntity.ok(parameterService.updateValue(id, command));
    }

    @DeleteMapping("/values/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Desactivar valor",
            description = "Borrado lógico: marca el valor como inactivo (`active = false`). Solo **SUPER_ADMIN**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Valor desactivado"),
            @ApiResponse(responseCode = "404", description = "Valor no encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Void> deactivateValue(
            @Parameter(description = "ID del valor", example = "10", required = true)
            @PathVariable Long id) {
        parameterService.deactivateValue(id);
        return ResponseEntity.noContent().build();
    }
}
