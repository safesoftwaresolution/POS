package com.sciencebot.pos.appointments;

import com.sciencebot.pos.appointments.internal.entities.Appointment;
import com.sciencebot.pos.appointments.internal.repositories.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Con Hibernate y H2 reales: el cruce de horarios se decide en la consulta. */
@SpringBootTest
@Transactional
class AppointmentOverlapQueryTest {

    private static final Long STORE = 9301L;
    private static final LocalDateTime NINE = LocalDateTime.of(2026, 11, 10, 9, 0);
    private static final LocalDateTime TEN = NINE.plusHours(1);

    @Autowired
    private AppointmentRepository repository;
    private Appointment nineToTen;

    @BeforeEach
    void setUp() {
        nineToTen = save(STORE, NINE, TEN, AppointmentStatus.SCHEDULED);
    }

    private Appointment save(Long store, LocalDateTime start, LocalDateTime end, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setStoreId(store);
        a.setCustomerId(1L);
        a.setCustomerName("Ana");
        a.setServiceId(1L);
        a.setServiceName("Corte");
        a.setStartAt(start);
        a.setEndAt(end);
        a.setStatus(status);
        return repository.saveAndFlush(a);
    }

    @Test
    void anyCrossingOfTheIntervalIsAnOverlap() {
        assertTrue(repository.existsOverlapping(STORE, NINE.plusMinutes(30), TEN.plusMinutes(30), null));
        assertTrue(repository.existsOverlapping(STORE, NINE.minusMinutes(30), NINE.plusMinutes(30), null));
        assertTrue(repository.existsOverlapping(STORE, NINE.plusMinutes(15), NINE.plusMinutes(45), null));
        assertTrue(repository.existsOverlapping(STORE, NINE, TEN, null));
    }

    @Test
    void touchingEndsAreNotAnOverlap() {
        assertFalse(repository.existsOverlapping(STORE, TEN, TEN.plusMinutes(30), null));
        assertFalse(repository.existsOverlapping(STORE, NINE.minusMinutes(30), NINE, null));
    }

    @Test
    void cancelledAppointmentsAndOtherStoresDoNotCount() {
        nineToTen.setStatus(AppointmentStatus.CANCELLED);
        repository.saveAndFlush(nineToTen);
        assertFalse(repository.existsOverlapping(STORE, NINE, TEN, null));

        save(9302L, NINE, TEN, AppointmentStatus.SCHEDULED);
        assertFalse(repository.existsOverlapping(STORE, NINE, TEN, null));
    }

    @Test
    void excludedAppointmentDoesNotConflictWithItself() {
        assertFalse(repository.existsOverlapping(STORE, NINE, TEN, nineToTen.getId()));
        assertTrue(repository.existsOverlapping(STORE, NINE, TEN, nineToTen.getId() + 1000));
    }
}
