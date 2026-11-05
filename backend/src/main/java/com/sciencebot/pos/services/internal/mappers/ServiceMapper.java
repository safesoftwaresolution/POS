package com.sciencebot.pos.services.internal.mappers;

import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.internal.entities.ServiceCategory;
import com.sciencebot.pos.services.internal.entities.ServiceItem;
import com.sciencebot.pos.services.internal.repositories.ServiceCategoryRepository;
import org.springframework.stereotype.Component;

@Component
public class ServiceMapper {

    private final ServiceCategoryRepository categoryRepository;

    public ServiceMapper(ServiceCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public ServiceDto toDto(ServiceItem service) {
        if (service == null) {
            return null;
        }
        String categoryName = categoryRepository.findById(service.getCategoryId())
                .map(ServiceCategory::getName)
                .orElse("Sin Categoría");

        return new ServiceDto(
                service.getId(),
                service.getCode(),
                service.getName(),
                service.getDescription(),
                service.getCategoryId(),
                categoryName,
                service.getPrice(),
                service.getTaxRate(),
                service.getUnspscCode(),
                service.getUnitMeasureCode(),
                service.getDurationMinutes(),
                service.isActive()
        );
    }
}
