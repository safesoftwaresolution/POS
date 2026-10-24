package com.sciencebot.pos.stores.internal.repositories;

import com.sciencebot.pos.stores.internal.entities.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanRepository extends JpaRepository<PlanEntity, String> {
    List<PlanEntity> findAllByOrderBySortOrderAsc();
}
