package com.sciencebot.pos.finance.internal.repositories;

import com.sciencebot.pos.finance.internal.entities.FixedCost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FixedCostRepository extends JpaRepository<FixedCost, Long> {

    Optional<FixedCost> findByIdAndStoreId(Long id, Long storeId);

    List<FixedCost> findByStoreIdOrderByNameAsc(Long storeId);

    boolean existsByStoreIdAndNameIgnoreCase(Long storeId, String name);

    boolean existsByStoreIdAndNameIgnoreCaseAndIdNot(Long storeId, String name, Long id);
}
