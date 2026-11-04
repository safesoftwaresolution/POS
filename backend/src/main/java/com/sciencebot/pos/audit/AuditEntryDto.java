package com.sciencebot.pos.audit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "Un cambio registrado en la bitácora de auditoría")
public record AuditEntryDto(
        @Schema(description = "Identificador del registro", example = "12")
        Long id,
        @Schema(description = "Tipo de entidad auditada", example = "CUSTOMER")
        String entityType,
        @Schema(description = "Identificador de la entidad auditada", example = "5")
        Long entityId,
        @Schema(description = "Acción realizada", example = "UPDATE")
        AuditAction action,
        @Schema(description = "Id del usuario que hizo el cambio (null si no se pudo identificar)", example = "3")
        Long userId,
        @Schema(description = "Usuario que hizo el cambio", example = "ana")
        String username,
        @Schema(description = "Campos modificados con su valor anterior y nuevo")
        Map<String, FieldChange> changes,
        @Schema(description = "Cuándo ocurrió el cambio")
        LocalDateTime createdAt
) {
}
