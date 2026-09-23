package com.sciencebot.pos.categories.internal.services;

import com.sciencebot.pos.categories.*;
import com.sciencebot.pos.categories.internal.entities.Subcategory;
import com.sciencebot.pos.categories.internal.mappers.SubcategoryMapper;
import com.sciencebot.pos.categories.internal.repositories.CategoryRepository;
import com.sciencebot.pos.categories.internal.repositories.SubcategoryRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A diferencia de CategoryServiceImpl (catálogo global, solo SUPER_ADMIN), las
 * subcategorías son por tienda: cada tienda crea, lee y borra únicamente las suyas,
 * igual que products/customers/suppliers (ver TenantContext).
 */
@Service
@Transactional(readOnly = true)
public class SubcategoryServiceImpl implements SubcategoryFacade {

    private final SubcategoryRepository subcategoryRepository;
    private final CategoryRepository categoryRepository;
    private final List<SubcategoryDeleteValidator> deleteValidators;
    private final SubcategoryMapper subcategoryMapper;

    public SubcategoryServiceImpl(
            SubcategoryRepository subcategoryRepository,
            CategoryRepository categoryRepository,
            List<SubcategoryDeleteValidator> deleteValidators,
            SubcategoryMapper subcategoryMapper) {
        this.subcategoryRepository = subcategoryRepository;
        this.categoryRepository = categoryRepository;
        this.deleteValidators = deleteValidators;
        this.subcategoryMapper = subcategoryMapper;
    }

    @Override
    public Optional<SubcategoryDto> getById(Long id) {
        return subcategoryRepository.findById(id)
                .filter(this::belongsToCurrentStore)
                .map(subcategoryMapper::toDto);
    }

    @Override
    public List<SubcategoryDto> listByCategory(Long categoryId) {
        return subcategoryRepository.findByStoreIdAndCategoryId(requireCurrentStoreId(), categoryId).stream()
                .map(subcategoryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<SubcategoryDto> listAll() {
        return subcategoryRepository.findByStoreId(requireCurrentStoreId()).stream()
                .map(subcategoryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SubcategoryDto createSubcategory(CreateSubcategoryCommand command) {
        validateName(command.name());
        if (command.categoryId() == null) {
            throw new IllegalArgumentException("La categoría es obligatoria");
        }
        if (!categoryRepository.existsById(command.categoryId())) {
            throw new IllegalArgumentException("La categoría especificada no existe");
        }

        Long storeId = requireCurrentStoreId();
        String name = command.name().trim();
        if (subcategoryRepository.existsByStoreIdAndCategoryIdAndNameIgnoreCase(storeId, command.categoryId(), name)) {
            throw new IllegalArgumentException("Ya existe una subcategoría con el nombre: " + name);
        }

        Subcategory subcategory = new Subcategory();
        subcategory.setStoreId(storeId);
        subcategory.setCategoryId(command.categoryId());
        subcategory.setName(name);
        subcategory.setDescription(command.description() != null ? command.description().trim() : null);

        Subcategory saved = subcategoryRepository.save(subcategory);
        return subcategoryMapper.toDto(saved);
    }

    @Override
    @Transactional
    public SubcategoryDto updateSubcategory(Long id, UpdateSubcategoryCommand command) {
        Subcategory subcategory = subcategoryRepository.findById(id)
                .filter(this::belongsToCurrentStore)
                .orElseThrow(() -> new EntityNotFoundException("Subcategoría no encontrada con ID: " + id));

        validateName(command.name());
        String newName = command.name().trim();
        subcategoryRepository.findByStoreIdAndCategoryIdAndNameIgnoreCase(subcategory.getStoreId(), subcategory.getCategoryId(), newName)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException("Ya existe una subcategoría con el nombre: " + newName);
                    }
                });

        subcategory.setName(newName);
        subcategory.setDescription(command.description() != null ? command.description().trim() : null);

        Subcategory saved = subcategoryRepository.save(subcategory);
        return subcategoryMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteSubcategory(Long id) {
        Subcategory subcategory = subcategoryRepository.findById(id)
                .filter(this::belongsToCurrentStore)
                .orElseThrow(() -> new EntityNotFoundException("Subcategoría no encontrada con ID: " + id));

        for (SubcategoryDeleteValidator validator : deleteValidators) {
            validator.validateSubcategoryBeforeDelete(subcategory.getId());
        }

        subcategoryRepository.delete(subcategory);
    }

    private boolean belongsToCurrentStore(Subcategory subcategory) {
        return subcategory.getStoreId().equals(TenantContext.getStoreId());
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la subcategoría es obligatorio");
        }
    }
}
