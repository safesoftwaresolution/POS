package com.sciencebot.pos.stores.internal.repositories;

import com.sciencebot.pos.stores.internal.entities.StoreDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreDocumentRepository extends JpaRepository<StoreDocumentEntity, Long> {
    List<StoreDocumentEntity> findByStoreId(Long storeId);
}