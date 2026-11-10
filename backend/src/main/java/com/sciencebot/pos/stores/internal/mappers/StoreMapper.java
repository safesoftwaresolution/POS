package com.sciencebot.pos.stores.internal.mappers;

import com.sciencebot.pos.stores.StoreCategoryDto;
import com.sciencebot.pos.stores.StoreDocumentDto;
import com.sciencebot.pos.stores.StoreDto;
import com.sciencebot.pos.stores.StoreTemplate;
import com.sciencebot.pos.stores.StoreTheme;
import com.sciencebot.pos.stores.internal.entities.StoreCategoryEntity;
import com.sciencebot.pos.stores.internal.entities.StoreDocumentEntity;
import com.sciencebot.pos.stores.internal.entities.StoreEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Component
public class StoreMapper {

    public StoreDto toDto(StoreEntity entity, StoreCategoryEntity category) {
        if (entity == null) return null;
        Set<StoreTemplate> templates = StoreTemplate.effective(category == null ? null : category.getTemplates(), entity.getExtraTemplates());
        return new StoreDto(
                entity.getId(),
                entity.getName(),
                entity.getStoreCategoryId(),
                category == null ? null : category.getName(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getWebsite(),
                entity.getAddress(),
                entity.getTaxId(),
                entity.getStatus(),
                entity.isEmailVerified(),
                entity.getRejectionReason(),
                entity.getCreatedAt(),
                entity.getPlanCode(),
                StoreTemplate.primary(templates).name(),
                (category == null ? StoreTheme.DEFAULT : category.getTheme()).name(),
                names(templates),
                names(entity.getExtraTemplates())
        );
    }

    public StoreCategoryDto toCategoryDto(StoreCategoryEntity entity) {
        if (entity == null) return null;
        return new StoreCategoryDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                StoreTemplate.primary(entity.getTemplates()).name(),
                entity.getTheme().name(),
                names(entity.getTemplates())
        );
    }

    /** Template keys in enum order (stable for the UI), without duplicates. */
    private static List<String> names(Collection<StoreTemplate> templates) {
        return templates == null ? List.of() : templates.stream().sorted().map(Enum::name).toList();
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