package com.sciencebot.pos.stores.internal.repositories;

import com.sciencebot.pos.stores.internal.entities.StoreEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<StoreEntity, Long> {

    Page<StoreEntity> findByStatus(String status, Pageable pageable);

    @Query("SELECT s FROM StoreEntity s WHERE (:status IS NULL OR s.status = :status) AND (:q IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT(\'%\',CAST(:q AS string),\'%\')) OR LOWER(s.email) LIKE LOWER(CONCAT(\'%\',CAST(:q AS string),\'%\')) OR LOWER(s.phone) LIKE LOWER(CONCAT(\'%\',CAST(:q AS string),\'%\')) OR LOWER(s.taxId) LIKE LOWER(CONCAT(\'%\',CAST(:q AS string),\'%\')))")
    Page<StoreEntity> searchStores(@Param("status") String status, @Param("q") String q, Pageable pageable);

    long countByStatus(String status);
    long countByEmailVerifiedTrue();
    boolean existsByEmail(String email);
}