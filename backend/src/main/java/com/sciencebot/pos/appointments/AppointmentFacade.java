package com.sciencebot.pos.appointments;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agenda de citas del local actual. Solo disponible para locales con plantilla APPOINTMENTS
 * (cualquier otro recibe AccessDeniedException).
 */
public interface AppointmentFacade {

    AppointmentDto create(CreateAppointmentCommand command);

    /** Citas cuyo inicio cae en [from, to), ordenadas por inicio; el rango máximo es de 62 días. */
    List<AppointmentDto> listBetween(LocalDateTime from, LocalDateTime to);

    /** Mueve una cita agendada conservando su duración. Una cita cancelada no se puede mover. */
    AppointmentDto reschedule(Long id, RescheduleAppointmentCommand command);

    /** Cancela la cita; repetirlo no cambia nada. */
    AppointmentDto cancel(Long id);
}
