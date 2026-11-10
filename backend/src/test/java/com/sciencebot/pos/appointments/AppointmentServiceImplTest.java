package com.sciencebot.pos.appointments;

import com.sciencebot.pos.appointments.internal.entities.Appointment;
import com.sciencebot.pos.appointments.internal.mappers.AppointmentMapper;
import com.sciencebot.pos.appointments.internal.repositories.AppointmentRepository;
import com.sciencebot.pos.appointments.internal.services.AppointmentServiceImpl;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.ServiceFacade;
import com.sciencebot.pos.stores.StoreFacade;
import com.sciencebot.pos.stores.StoreTemplate;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AppointmentServiceImplTest {

    private static final Long STORE_ID = 7L;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 15, 0);

    private final AppointmentRepository repository = mock(AppointmentRepository.class);
    private final ServiceFacade serviceFacade = mock(ServiceFacade.class);
    private final CustomerFacade customerFacade = mock(CustomerFacade.class);
    private final StoreFacade storeFacade = mock(StoreFacade.class);
    private AppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setStoreId(STORE_ID);
        service = new AppointmentServiceImpl(repository, new AppointmentMapper(), serviceFacade, customerFacade, storeFacade);
        when(storeFacade.getTemplatesForStore(STORE_ID)).thenReturn(EnumSet.of(StoreTemplate.APPOINTMENTS));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void givenCustomerAndService(Integer durationMinutes, boolean active) {
        CustomerDto customer = mock(CustomerDto.class);
        when(customer.fullName()).thenReturn("Ana Pérez");
        when(customerFacade.getById(3L)).thenReturn(Optional.of(customer));
        ServiceDto catalogService = mock(ServiceDto.class);
        when(catalogService.name()).thenReturn("Corte clásico");
        when(catalogService.durationMinutes()).thenReturn(durationMinutes);
        when(catalogService.active()).thenReturn(active);
        when(serviceFacade.getById(5L)).thenReturn(Optional.of(catalogService));
    }

    private static Appointment stored(AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setId(1L);
        a.setStoreId(STORE_ID);
        a.setStartAt(START);
        a.setEndAt(START.plusMinutes(45));
        a.setStatus(status);
        return a;
    }

    @Test
    void create_UsesTheServiceDurationAndSnapshotsTheNames() {
        givenCustomerAndService(45, true);

        AppointmentDto dto = service.create(new CreateAppointmentCommand(3L, 5L, START, "Sin prisa"));

        assertEquals(START, dto.startAt());
        assertEquals(START.plusMinutes(45), dto.endAt());
        assertEquals("SCHEDULED", dto.status());
        assertEquals("Ana Pérez", dto.customerName());
        assertEquals("Corte clásico", dto.serviceName());
        assertEquals("Sin prisa", dto.notes());
        verify(repository).save(argThat(a -> STORE_ID.equals(a.getStoreId())));
    }

    @Test
    void create_ServiceWithoutDuration_DefaultsToThirtyMinutes() {
        givenCustomerAndService(null, true);

        AppointmentDto dto = service.create(new CreateAppointmentCommand(3L, 5L, START, null));

        assertEquals(START.plusMinutes(30), dto.endAt());
    }

    @Test
    void create_StoreWithoutTheAppointmentsTemplate_IsRefused() {
        when(storeFacade.getTemplatesForStore(STORE_ID)).thenReturn(EnumSet.of(StoreTemplate.RETAIL, StoreTemplate.FOOD));

        assertThrows(AccessDeniedException.class,
                () -> service.create(new CreateAppointmentCommand(3L, 5L, START, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void create_StoreWithAppointmentsAmongSeveralTemplates_IsAllowed() {
        when(storeFacade.getTemplatesForStore(STORE_ID)).thenReturn(EnumSet.of(StoreTemplate.RETAIL, StoreTemplate.APPOINTMENTS));
        givenCustomerAndService(30, true);

        assertEquals("SCHEDULED", service.create(new CreateAppointmentCommand(3L, 5L, START, null)).status());
    }

    @Test
    void create_RequiresCustomerServiceAndStart() {
        givenCustomerAndService(30, true);

        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateAppointmentCommand(null, 5L, START, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateAppointmentCommand(3L, null, START, null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateAppointmentCommand(3L, 5L, null, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void create_UnknownCustomerOrService_IsNotFound() {
        givenCustomerAndService(30, true);
        when(customerFacade.getById(3L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.create(new CreateAppointmentCommand(3L, 5L, START, null)));

        givenCustomerAndService(30, true);
        when(serviceFacade.getById(5L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.create(new CreateAppointmentCommand(3L, 5L, START, null)));
    }

    @Test
    void create_InactiveService_IsRefused() {
        givenCustomerAndService(30, false);

        assertThrows(IllegalArgumentException.class, () -> service.create(new CreateAppointmentCommand(3L, 5L, START, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void create_OverlappingAnotherAppointment_IsRefusedWith409Semantics() {
        givenCustomerAndService(60, true);
        when(repository.existsOverlapping(STORE_ID, START, START.plusMinutes(60), null)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.create(new CreateAppointmentCommand(3L, 5L, START, null)));
        assertEquals("Ya hay una cita agendada en ese horario", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void reschedule_IntoAnOccupiedSlot_IsRefusedAndIgnoresItself() {
        when(repository.findByIdAndStoreId(1L, STORE_ID)).thenReturn(Optional.of(stored(AppointmentStatus.SCHEDULED)));
        LocalDateTime newStart = START.plusHours(1);
        when(repository.existsOverlapping(STORE_ID, newStart, newStart.plusMinutes(45), 1L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.reschedule(1L, new RescheduleAppointmentCommand(newStart)));
        verify(repository, never()).save(any());
    }

    @Test
    void reschedule_MovesTheAppointmentKeepingItsDuration() {
        when(repository.findByIdAndStoreId(1L, STORE_ID)).thenReturn(Optional.of(stored(AppointmentStatus.SCHEDULED)));
        LocalDateTime newStart = START.plusDays(1).plusHours(2);

        AppointmentDto dto = service.reschedule(1L, new RescheduleAppointmentCommand(newStart));

        assertEquals(newStart, dto.startAt());
        assertEquals(newStart.plusMinutes(45), dto.endAt());
        assertEquals("SCHEDULED", dto.status());
    }

    @Test
    void reschedule_CancelledAppointmentOrUnknownOrMissingStart_IsRefused() {
        when(repository.findByIdAndStoreId(1L, STORE_ID)).thenReturn(Optional.of(stored(AppointmentStatus.CANCELLED)));
        when(repository.findByIdAndStoreId(2L, STORE_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.reschedule(1L, new RescheduleAppointmentCommand(START)));
        assertThrows(EntityNotFoundException.class, () -> service.reschedule(2L, new RescheduleAppointmentCommand(START)));
        assertThrows(IllegalArgumentException.class, () -> service.reschedule(1L, new RescheduleAppointmentCommand(null)));
    }

    @Test
    void cancel_MarksItCancelledAndRepeatingItChangesNothing() {
        Appointment appointment = stored(AppointmentStatus.SCHEDULED);
        when(repository.findByIdAndStoreId(1L, STORE_ID)).thenReturn(Optional.of(appointment));

        assertEquals("CANCELLED", service.cancel(1L).status());
        assertEquals("CANCELLED", service.cancel(1L).status());
        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
    }

    @Test
    void cancel_AppointmentOfAnotherStore_IsNotFound() {
        when(repository.findByIdAndStoreId(1L, STORE_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.cancel(1L));
    }

    @Test
    void listBetween_ReturnsTheStoreAppointmentsOfTheRange() {
        LocalDateTime to = START.plusDays(7);
        when(repository.findInRange(STORE_ID, START, to)).thenReturn(List.of(stored(AppointmentStatus.SCHEDULED)));

        assertEquals(1, service.listBetween(START, to).size());
    }

    @Test
    void listBetween_InvalidOrTooWideRange_IsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.listBetween(START, START));
        assertThrows(IllegalArgumentException.class, () -> service.listBetween(START, START.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.listBetween(START, START.plusDays(63)));
        assertThrows(IllegalArgumentException.class, () -> service.listBetween(null, START));
    }
}
