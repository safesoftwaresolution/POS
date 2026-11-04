package com.sciencebot.pos.services.internal.mappers;

import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.services.ServiceDto;
import com.sciencebot.pos.services.internal.entities.ServiceItem;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class ServiceMapper {

    private final CategoryFacade categoryFacade;

    public ServiceMapper(@Lazy CategoryFacade categoryFacade) {
        this.categoryFacade = categoryFacade;
    }

    public ServiceDto toDto(ServiceItem service) {
        if (service == null) {
            return null;
        }
        String categoryName = categoryFacade.getById(service.getCategoryId())
                .map(category -> category.name())
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
