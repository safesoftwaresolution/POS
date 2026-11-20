package com.sciencebot.pos.businesses.internal.repositories;

import com.sciencebot.pos.businesses.internal.entities.BusinessEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessRepository extends JpaRepository<BusinessEntity, Long> {

    boolean existsByOwnerUserId(Long ownerUserId);

    java.util.Optional<BusinessEntity> findByOwnerUserId(Long ownerUserId);

    /** Serializes branch creation so two requests cannot exceed {@code max_branches}. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BusinessEntity b WHERE b.id = :id")
    java.util.Optional<BusinessEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT b FROM BusinessEntity b WHERE :q IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))")
    Page<BusinessEntity> search(@Param("q") String q, Pageable pageable);
}
