package com.sciencebot.pos.services.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.services.CreateServiceCategoryCommand;
import com.sciencebot.pos.services.ServiceCategoryDto;
import com.sciencebot.pos.services.ServiceCategoryFacade;
import com.sciencebot.pos.services.internal.entities.ServiceCategory;
import com.sciencebot.pos.services.internal.mappers.ServiceCategoryMapper;
import com.sciencebot.pos.services.internal.repositories.ServiceCategoryRepository;
import com.sciencebot.pos.services.internal.repositories.ServiceItemRepository;
import com.sciencebot.pos.stores.StoreCategoryDto;
import com.sciencebot.pos.stores.StoreFacade;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ServiceCategoryServiceImpl implements ServiceCategoryFacade {

    private static final int MAX_NAME_LENGTH = 100;

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceItemRepository serviceRepository;
    private final ServiceCategoryMapper mapper;
    private final StoreFacade storeFacade;

    public ServiceCategoryServiceImpl(ServiceCategoryRepository categoryRepository,
                                      ServiceItemRepository serviceRepository,
                                      ServiceCategoryMapper mapper,
                                      StoreFacade storeFacade) {
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.mapper = mapper;
        this.storeFacade = storeFacade;
    }

    @Override
    public List<ServiceCategoryDto> listForCurrentStore() {
        Long storeCategoryId = currentStoreCategoryId();
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(c -> isOfferedTo(c, storeCategoryId))
                .map(mapper::toDto).toList();
    }

    @Override
    public List<ServiceCategoryDto> listAll() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public ServiceCategoryDto create(CreateServiceCategoryCommand command) {
        String name = requireName(command.name());
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Ya existe una categoría de servicios con el nombre: " + name);
        }
        ServiceCategory category = new ServiceCategory();
        category.setName(name);
        category.setDescription(blankToNull(command.description()));
        category.setStoreCategoryIds(validStoreCategoryIds(command.storeCategoryIds()));
        return mapper.toDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public ServiceCategoryDto update(Long id, CreateServiceCategoryCommand command) {
        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categoría de servicios no encontrada con ID: " + id));
        String name = requireName(command.name());
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("Ya existe una categoría de servicios con el nombre: " + name);
        }
        category.setName(name);
        category.setDescription(blankToNull(command.description()));
        category.setStoreCategoryIds(validStoreCategoryIds(command.storeCategoryIds()));
        return mapper.toDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categoría de servicios no encontrada con ID: " + id));
        if (serviceRepository.existsByCategoryIdAndActiveTrue(id)) {
            throw new IllegalStateException("No se puede desactivar la categoría porque tiene servicios activos");
        }
        category.setActive(false);
        categoryRepository.save(category);
    }

    /** Valida que el local actual pueda usar la categoría (existe, está activa y se ofrece a su categoría de local). */
    public void requireAvailableForCurrentStore(Long categoryId) {
        if (categoryId == null) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        ServiceCategory category = categoryRepository.findById(categoryId)
                .filter(ServiceCategory::isActive)
                .orElseThrow(() -> new IllegalArgumentException("La categoría especificada no existe"));
        if (!isOfferedTo(category, currentStoreCategoryId())) {
            throw new IllegalArgumentException("La categoría de servicios no está disponible para este tipo de local");
        }
    }

    private static boolean isOfferedTo(ServiceCategory category, Long storeCategoryId) {
        Set<Long> links = category.getStoreCategoryIds();
        return links.isEmpty() || (storeCategoryId != null && links.contains(storeCategoryId));
    }

    private Long currentStoreCategoryId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeFacade.getById(storeId).storeCategoryId();
    }

    private Set<Long> validStoreCategoryIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        Set<Long> known = storeFacade.listCategories().stream().map(StoreCategoryDto::id).collect(Collectors.toSet());
        Set<Long> requested = new HashSet<>(ids);
        if (!known.containsAll(requested)) {
            throw new IllegalArgumentException("Alguna categoría de local no existe");
        }
        return requested;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("El nombre de la categoría no puede superar los " + MAX_NAME_LENGTH + " caracteres");
        }
        return name.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
