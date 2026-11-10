package com.sciencebot.pos.appointments;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Cita agendada en el calendario del local")
public record AppointmentDto(
        @Schema(description = "Identificador de la cita", example = "1") Long id,
        @Schema(description = "Cliente", example = "3") Long customerId,
        @Schema(description = "Nombre del cliente al agendar", example = "Ana Pérez") String customerName,
        @Schema(description = "Servicio", example = "5") Long serviceId,
        @Schema(description = "Nombre del servicio al agendar", example = "Corte clásico") String serviceName,
        @Schema(description = "Inicio de la cita", example = "2026-11-10T15:00:00") LocalDateTime startAt,
        @Schema(description = "Fin de la cita (inicio + duración del servicio)", example = "2026-11-10T15:45:00") LocalDateTime endAt,
        @Schema(description = "SCHEDULED o CANCELLED", example = "SCHEDULED") String status,
        @Schema(description = "Notas opcionales", example = "Prefiere la máquina en 2") String notes
) {}
