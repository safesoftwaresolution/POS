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
}
