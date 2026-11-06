package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Código del set de pruebas (TestSetId) que la DIAN le dio al dueño al registrar a Alegra como proveedor. */
@Schema(description = "Inicio de la habilitación ante la DIAN")
public record DianEnablementRequest(
        @Schema(description = "TestSetId que entregó la DIAN (UUID)", example = "d7b8f2c4-1a2b-4c3d-9e8f-0a1b2c3d4e5f")
        @NotBlank(message = "El código del set de pruebas es obligatorio")
        @Pattern(regexp = "\\s*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\s*",
                message = "El código del set de pruebas no tiene el formato correcto")
        String testSetId
) {}
