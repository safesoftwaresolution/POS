package com.sciencebot.pos.appointments.internal.repositories;

import com.sciencebot.pos.appointments.internal.entities.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndStoreId(Long id, Long storeId);

    @Query("SELECT a FROM Appointment a WHERE a.storeId = :storeId AND a.startAt >= :from AND a.startAt < :to ORDER BY a.startAt")
    List<Appointment> findInRange(@Param("storeId") Long storeId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    /** Citas agendadas (no canceladas) del local que se cruzan con [start, end); {@code excludeId} omite la cita que se está moviendo. */
    @Query("SELECT COUNT(a) > 0 FROM Appointment a WHERE a.storeId = :storeId AND a.status = com.sciencebot.pos.appointments.AppointmentStatus.SCHEDULED "
            + "AND a.startAt < :end AND a.endAt > :start AND (:excludeId IS NULL OR a.id <> :excludeId)")
    boolean existsOverlapping(@Param("storeId") Long storeId,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end,
                              @Param("excludeId") Long excludeId);
}
