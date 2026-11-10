package com.sciencebot.pos.services;

import java.util.List;

public interface ServiceCategoryFacade {

    /** Categorías activas que el local actual puede usar: las de su categoría de local más las generales (sin vínculo). */
    List<ServiceCategoryDto> listForCurrentStore();

    /** Todas las categorías activas del catálogo (SUPER_ADMIN). */
    List<ServiceCategoryDto> listAll();

    ServiceCategoryDto create(CreateServiceCategoryCommand command);

    ServiceCategoryDto update(Long id, CreateServiceCategoryCommand command);

    /** Baja lógica; falla si aún tiene servicios activos. */
    void deactivate(Long id);
}
