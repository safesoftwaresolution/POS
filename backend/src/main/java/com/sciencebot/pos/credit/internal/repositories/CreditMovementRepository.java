package com.sciencebot.pos.credit.internal.repositories;

import com.sciencebot.pos.credit.CreditMovementType;
import com.sciencebot.pos.credit.internal.entities.CreditMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditMovementRepository extends JpaRepository<CreditMovement, Long> {

    Page<CreditMovement> findByStoreIdAndAccountIdOrderByCreatedAtDescIdDesc(Long storeId, Long accountId, Pageable pageable);

    Optional<CreditMovement> findFirstByStoreIdAndAccountIdOrderByCreatedAtDescIdDesc(Long storeId, Long accountId);

    /** ¿Este usuario le fió al cliente de esta cuenta? Decide qué cuentas ve un vendedor. */
    boolean existsByStoreIdAndAccountIdAndTypeAndUserId(Long storeId, Long accountId, CreditMovementType type, Long userId);
}
