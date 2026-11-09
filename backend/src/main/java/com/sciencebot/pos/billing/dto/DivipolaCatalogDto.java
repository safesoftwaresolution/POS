package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Catálogo DIVIPOLA del DANE: departamentos y municipios de Colombia con su código. Lo usan los formularios de datos
 * fiscales para que el usuario elija su municipio por nombre en lugar de escribir el código. Inmutable.
 */
@Schema(description = "Departamentos y municipios de Colombia (DIVIPOLA del DANE)")
public record DivipolaCatalogDto(
        @Schema(description = "Origen, fecha de corte y licencia de los datos")
        String source,
        @Schema(description = "Departamentos, por código")
        List<Department> departments,
        @Schema(description = "Municipios, por código")
        List<Municipality> municipalities
) {

    public DivipolaCatalogDto {
        departments = departments != null ? List.copyOf(departments) : List.of();
        municipalities = municipalities != null ? List.copyOf(municipalities) : List.of();
    }

    @Schema(description = "Departamento")
    public record Department(
            @Schema(description = "Código DANE de 2 dígitos", example = "05") String code,
            @Schema(description = "Nombre", example = "Antioquia") String name
    ) {}

    @Schema(description = "Municipio")
    public record Municipality(
            @Schema(description = "Código DANE de 5 dígitos", example = "05001") String code,
            @Schema(description = "Nombre", example = "Medellín") String name,
            @Schema(description = "Código DANE del departamento (los dos primeros dígitos)", example = "05")
            String departmentCode
    ) {}
}
