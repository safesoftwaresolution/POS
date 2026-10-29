package com.sciencebot.pos.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ServiceFacade {

    /** Servicio del local actual (activo o no); vacío si no existe o es de otro local. */
    Optional<ServiceDto> getById(Long id);

    ServiceDto createService(CreateServiceCommand command);

    ServiceDto updateService(Long id, UpdateServiceCommand command);

    void changeStatus(Long id, boolean active);

    /** Baja lógica: el servicio queda inactivo (las ventas ya emitidas lo siguen referenciando). */
    void deleteService(Long id);

    Page<ServiceDto> searchServices(String search, Long categoryId, Boolean active, Pageable pageable);
}
