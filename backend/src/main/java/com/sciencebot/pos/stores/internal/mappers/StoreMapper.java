package com.sciencebot.pos.stores.internal.mappers;

import com.sciencebot.pos.stores.StoreCategoryDto;
import com.sciencebot.pos.stores.StoreDocumentDto;
import com.sciencebot.pos.stores.StoreDto;
import com.sciencebot.pos.stores.internal.entities.StoreCategoryEntity;
import com.sciencebot.pos.stores.internal.entities.StoreDocumentEntity;
import com.sciencebot.pos.stores.internal.entities.StoreEntity;
import org.springframework.stereotype.Component;

@Component
public class StoreMapper {

    public StoreDto toDto(StoreEntity entity, String categoryName) {
        if (entity == null) return null;
        return new StoreDto(
                entity.getId(),
                entity.getName(),
                entity.getStoreCategoryId(),
                categoryName,
                entity.getPhone(),
                entity.getEmail(),
                entity.getWebsite(),
                entity.getAddress(),
                entity.getTaxId(),
                entity.getStatus(),
                entity.isEmailVerified(),
                entity.getRejectionReason(),
                entity.getCreatedAt()
        );
    }

    public StoreCategoryDto toCategoryDto(StoreCategoryEntity entity) {
        if (entity == null) return null;
        return new StoreCategoryDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive()
        );
    }

    public StoreDocumentDto toDocumentDto(StoreDocumentEntity entity) {
        if (entity == null) return null;
        return new StoreDocumentDto(
                entity.getId(),
                entity.getStoreId(),
                entity.getDocumentType(),
                entity.getDocumentUrl(),
                entity.getStatus(),
                entity.getRejectionReason(),
                entity.getUploadedAt(),
                entity.getVerifiedAt()
        );
    }
}