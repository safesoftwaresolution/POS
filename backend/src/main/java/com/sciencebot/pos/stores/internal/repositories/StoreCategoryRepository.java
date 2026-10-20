package com.sciencebot.pos.stores.internal.repositories;

import com.sciencebot.pos.stores.internal.entities.StoreCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreCategoryRepository extends JpaRepository<StoreCategoryEntity, Long> {
    List<StoreCategoryEntity> findByActiveTrue();
    boolean existsByNameIgnoreCase(String name);
}