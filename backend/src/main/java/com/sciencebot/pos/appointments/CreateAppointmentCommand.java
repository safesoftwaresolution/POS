package com.sciencebot.pos.appointments;

import java.time.LocalDateTime;

/** Cliente, servicio e inicio son obligatorios; el fin se calcula con la duración del servicio. */
public record CreateAppointmentCommand(Long customerId, Long serviceId, LocalDateTime startAt, String notes) {}
