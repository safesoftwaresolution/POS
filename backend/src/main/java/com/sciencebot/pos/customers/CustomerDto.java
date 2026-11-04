package com.sciencebot.pos.customers;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Datos completos de un cliente")
public record CustomerDto(
        @Schema(description = "Identificador único del cliente", example = "1")
        Long id,
        @Schema(description = "Nombre completo del cliente", example = "Juan García López")
        String fullName,
        @Schema(description = "Número de identificación (cédula, NIT)", example = "1234567890")
        String identification,
        @Schema(description = "Correo electrónico", example = "juan.garcia@email.com")
        String email,
        @Schema(description = "Número de teléfono", example = "+57 310 123 4567")
        String phone,
        @Schema(description = "Dirección postal", example = "Calle 123 # 45-67, Bogotá")
        String address,
        @Schema(description = "Si el cliente está activo en el sistema", example = "true")
        boolean active,
        @Schema(description = "Dígito de verificación DIAN (para NIT)", example = "9")
        String dv,
        @Schema(description = "Tipo de organización jurídica DIAN (1=Persona Jurídica, 13=Persona Natural)", example = "13")
        Integer legalOrganizationId,
        @Schema(description = "Tipo de tributo / responsabilidad DIAN (01=IVA, 21=Consumidor Final / No Responsable)", example = "21")
        Integer tributeId,
        @Schema(description = "Código de municipio DANE (ej. 11001 Bogotá)", example = "11001")
        Integer municipalityId,
        @Schema(description = "Usuario que creó el cliente (null en los registrados antes de la auditoría)", example = "ana")
        String createdByUsername,
        @Schema(description = "Último usuario que editó el cliente", example = "carlos")
        String updatedByUsername,
        @Schema(description = "Fecha de creación")
        LocalDateTime createdAt,
        @Schema(description = "Fecha de la última edición")
        LocalDateTime updatedAt,
        @Schema(description = "Si el usuario actual puede editar este cliente (un vendedor solo edita los que creó)", example = "true")
        boolean editable
) {
    /** Sin datos de auditoría: lo usan los llamadores que solo necesitan los datos del cliente. */
    public CustomerDto(Long id, String fullName, String identification, String email, String phone, String address,
                       boolean active, String dv, Integer legalOrganizationId, Integer tributeId, Integer municipalityId) {
        this(id, fullName, identification, email, phone, address, active, dv, legalOrganizationId, tributeId,
                municipalityId, null, null, null, null, true);
    }

    public CustomerDto(Long id, String fullName, String identification, String email, String phone, String address, boolean active) {
        this(id, fullName, identification, email, phone, address, active, null, 13, 21, null);
    }
}
