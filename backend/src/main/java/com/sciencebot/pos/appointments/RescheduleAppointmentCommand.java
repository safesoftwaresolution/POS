package com.sciencebot.pos.appointments;

import java.time.LocalDateTime;

/** Nuevo inicio de la cita; la duración se conserva. */
public record RescheduleAppointmentCommand(LocalDateTime startAt) {}
