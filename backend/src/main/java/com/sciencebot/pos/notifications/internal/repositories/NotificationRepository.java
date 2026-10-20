package com.sciencebot.pos.notifications.internal.repositories;

import com.sciencebot.pos.notifications.internal.entities.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    long countByStoreIdAndReadFalse(Long storeId);

    @Modifying
    @Query("update NotificationEntity n set n.read = true where n.storeId = :storeId and n.read = false")
    void markAllReadByStoreId(@Param("storeId") Long storeId);
}
