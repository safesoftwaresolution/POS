package com.sciencebot.pos.support.internal.repositories;

import com.sciencebot.pos.support.internal.entities.SupportTicketEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportTicketRepository extends JpaRepository<SupportTicketEntity, Long> {

    java.util.Optional<SupportTicketEntity> findByTicketNumber(String ticketNumber);

    @Query("SELECT t FROM SupportTicketEntity t WHERE (:type IS NULL OR t.type = :type) AND (:status IS NULL OR t.status = :status) AND (:priority IS NULL OR t.priority = :priority) AND (:storeId IS NULL OR t.storeId = :storeId) AND (:q IS NULL OR LOWER(t.title) LIKE :q OR LOWER(t.contactEmail) LIKE :q OR LOWER(t.ticketNumber) LIKE :q)")
    Page<SupportTicketEntity> searchTickets(
            @Param("type") String type,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("storeId") Long storeId,
            @Param("q") String q,
            Pageable pageable);

    long countByStatus(String status);
    long countByTypeAndPriority(String type, String priority);
}