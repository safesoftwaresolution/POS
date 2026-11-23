package com.sciencebot.pos.finance.internal.repositories;

import com.sciencebot.pos.finance.internal.entities.FinanceMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface FinanceMovementRepository extends JpaRepository<FinanceMovement, Long> {

    Optional<FinanceMovement> findByIdAndStoreId(Long id, Long storeId);

    Page<FinanceMovement> findByStoreIdAndMovementDateBetweenOrderByMovementDateDescIdDesc(
            Long storeId, LocalDate from, LocalDate to, Pageable pageable);
}
