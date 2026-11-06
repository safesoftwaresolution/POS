package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos fiscales del local. Si aún no los guarda, {@code saved} es {@code false} y los campos son una sugerencia
 * tomada de los datos del local (nombre, NIT, correo, teléfono y dirección) para precargar el formulario.
 */
@Schema(description = "Datos fiscales del local (guardados o sugeridos)")
public record FiscalProfileDto(
        String legalName,
        String tradeName,
        String nit,
        String dv,
        Integer organizationType,
        String regimeCode,
        String economicActivity,
        String email,
        String phone,
        String address,
        String cityCode,
        String departmentCode,
        @Schema(description = "true si son los datos guardados; false si es una sugerencia")
        boolean saved
) {}
