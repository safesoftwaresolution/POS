package com.sciencebot.pos.appointments.internal.mappers;

import com.sciencebot.pos.appointments.AppointmentDto;
import com.sciencebot.pos.appointments.internal.entities.Appointment;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMapper {

    public AppointmentDto toDto(Appointment entity) {
        if (entity == null) return null;
        return new AppointmentDto(
                entity.getId(),
                entity.getCustomerId(),
                entity.getCustomerName(),
                entity.getServiceId(),
                entity.getServiceName(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getStatus().name(),
                entity.getNotes()
        );
    }
}
