package com.sciencebot.pos.services.internal.mappers;

import com.sciencebot.pos.services.ServiceCategoryDto;
import com.sciencebot.pos.services.internal.entities.ServiceCategory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ServiceCategoryMapper {

    public ServiceCategoryDto toDto(ServiceCategory category) {
        List<Long> storeCategoryIds = category.getStoreCategoryIds().stream().sorted().toList();
        return new ServiceCategoryDto(category.getId(), category.getName(), category.getDescription(),
                category.isActive(), storeCategoryIds);
    }
}
