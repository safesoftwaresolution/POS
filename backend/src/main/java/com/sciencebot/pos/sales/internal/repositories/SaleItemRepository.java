package com.sciencebot.pos.sales.internal.repositories;

import com.sciencebot.pos.sales.internal.entities.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
}
