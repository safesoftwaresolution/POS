package com.sciencebot.pos.appointments.internal.services;

import com.sciencebot.pos.appointments.AppointmentDto;
import com.sciencebot.pos.appointments.AppointmentFacade;
import com.sciencebot.pos.appointments.AppointmentStatus;
import com.sciencebot.pos.appointments.CreateAppointmentCommand;
import com.sciencebot.pos.appointments.RescheduleAppointmentCommand;
import com.sciencebot.pos.appointments.internal.entities.Appointment;
import com.sciencebot.pos.appointments.internal.mappers.AppointmentMapper;
import com.sciencebot.pos.appointments.internal.repositories.AppointmentRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.stores.StoreFacade;
import com.sciencebot.pos.stores.StoreTemplate;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AppointmentServiceImpl implements AppointmentFacade {

    private static final int DEFAULT_DURATION_MINUTES = 30;
    private static final int MAX_RANGE_DAYS = 62;
    private static final int MAX_NOTES_LENGTH = 255;

    private final AppointmentRepository appointmentRepository;
    private final AppointmentMapper appointmentMapper;
    private final ServiceFacade serviceFacade;
    private final CustomerFacade customerFacade;
    private final StoreFacade storeFacade;

    public AppointmentServiceImpl(AppointmentRepository appointmentRepository,
                                  AppointmentMapper appointmentMapper,
                                  ServiceFacade serviceFacade,
                                  CustomerFacade customerFacade,
                                  StoreFacade storeFacade) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentMapper = appointmentMapper;
        this.serviceFacade = serviceFacade;
        this.customerFacade = customerFacade;
        this.storeFacade = storeFacade;
    }

    @Override
    @Transactional
    public AppointmentDto create(CreateAppointmentCommand command) {
        Long storeId = requireAppointmentsStore();
        if (command.customerId() == null) throw new IllegalArgumentException("El cliente es obligatorio");
        if (command.serviceId() == null) throw new IllegalArgumentException("El servicio es obligatorio");
        if (command.startAt() == null) throw new IllegalArgumentException("La fecha y hora de inicio son obligatorias");
        if (command.notes() != null && command.notes().length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("Las notas no pueden superar " + MAX_NOTES_LENGTH + " caracteres");
        }

        CustomerDto customer = customerFacade.getById(command.customerId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + command.customerId()));
        ServiceDto catalogService = serviceFacade.getById(command.serviceId())
                .orElseThrow(() -> new EntityNotFoundException("Servicio no encontrado con ID: " + command.serviceId()));
        if (!catalogService.active()) {
            throw new IllegalArgumentException("El servicio está inactivo y no se puede agendar");
        }
        Integer duration = catalogService.durationMinutes();

        LocalDateTime endAt = command.startAt().plusMinutes(duration != null && duration > 0 ? duration : DEFAULT_DURATION_MINUTES);
        requireFreeSlot(storeId, command.startAt(), endAt, null);

        Appointment appointment = new Appointment();
        appointment.setStoreId(storeId);
        appointment.setCustomerId(command.customerId());
        appointment.setCustomerName(customer.fullName());
        appointment.setServiceId(command.serviceId());
        appointment.setServiceName(catalogService.name());
        appointment.setStartAt(command.startAt());
        appointment.setEndAt(endAt);
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setNotes(command.notes());
        return appointmentMapper.toDto(appointmentRepository.save(appointment));
    }

    @Override
    public List<AppointmentDto> listBetween(LocalDateTime from, LocalDateTime to) {
        Long storeId = requireAppointmentsStore();
        if (from == null || to == null || !to.isAfter(from)) {
            throw new IllegalArgumentException("El rango de fechas no es válido");
        }
        if (Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("El rango máximo es de " + MAX_RANGE_DAYS + " días");
        }
        return appointmentRepository.findInRange(storeId, from, to).stream().map(appointmentMapper::toDto).toList();
    }

    @Override
    @Transactional
    public AppointmentDto reschedule(Long id, RescheduleAppointmentCommand command) {
        Appointment appointment = findOwn(id);
        if (command.startAt() == null) throw new IllegalArgumentException("La nueva fecha y hora son obligatorias");
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Una cita cancelada no se puede mover");
        }
        Duration duration = Duration.between(appointment.getStartAt(), appointment.getEndAt());
        LocalDateTime newEnd = command.startAt().plus(duration);
        requireFreeSlot(appointment.getStoreId(), command.startAt(), newEnd, appointment.getId());
        appointment.setStartAt(command.startAt());
        appointment.setEndAt(newEnd);
        return appointmentMapper.toDto(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional
    public AppointmentDto cancel(Long id) {
        Appointment appointment = findOwn(id);
        if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment = appointmentRepository.save(appointment);
        }
        return appointmentMapper.toDto(appointment);
    }

    /** El local tiene una sola agenda: no puede haber dos citas agendadas que se crucen (los extremos pueden tocarse). */
    private void requireFreeSlot(Long storeId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (appointmentRepository.existsOverlapping(storeId, start, end, excludeId)) {
            throw new IllegalStateException("Ya hay una cita agendada en ese horario");
        }
    }

    private Appointment findOwn(Long id) {
        Long storeId = requireAppointmentsStore();
        return appointmentRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new EntityNotFoundException("Cita no encontrada con ID: " + id));
    }

    /** Local actual, que además debe tener la plantilla de citas (de su categoría o como extra) (el backend decide, no el menú del frontend). */
    private Long requireAppointmentsStore() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        if (!storeFacade.getTemplatesForStore(storeId).contains(StoreTemplate.APPOINTMENTS)) {
            throw new AccessDeniedException("Las citas solo están disponibles para locales con plantilla de citas.");
        }
        return storeId;
    }
}
