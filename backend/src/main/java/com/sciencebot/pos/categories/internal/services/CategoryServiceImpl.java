package com.sciencebot.pos.categories.internal.services;

import com.sciencebot.pos.categories.*;
import com.sciencebot.pos.categories.internal.entities.Category;
import com.sciencebot.pos.categories.internal.repositories.CategoryRepository;
import com.sciencebot.pos.categories.internal.repositories.SubcategoryRepository;
import com.sciencebot.pos.categories.internal.mappers.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryFacade {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategoryRepository;
    private final List<CategoryDeleteValidator> deleteValidators;
    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(
            CategoryRepository categoryRepository,
            SubcategoryRepository subcategoryRepository,
            List<CategoryDeleteValidator> deleteValidators,
            CategoryMapper categoryMapper
    ) {
        this.categoryRepository = categoryRepository;
        this.subcategoryRepository = subcategoryRepository;
        this.deleteValidators = deleteValidators;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public Optional<CategoryDto> getById(Long id) {
        return categoryRepository.findById(id).map(categoryMapper::toDto); 
    }


    @Override
    @Transactional
    public CategoryDto createCategory(CreateCategoryCommand command) {
        validateCategoryName(command.name());
        validateCategoryNameUniqueness(command.name(), null);

        Category category = new Category();
        category.setName(command.name().trim());
        category.setDescription(command.description() != null ? command.description().trim() : null);

        Category saved = categoryRepository.save(category);
        return categoryMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long id, UpdateCategoryCommand command) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Categoría no encontrada con ID: " + id));

        validateCategoryName(command.name());
        String newName = command.name().trim(); 
        validateCategoryNameUniqueness(newName, id);

        category.setName(newName);
        category.setDescription(command.description() != null ? command.description().trim() : null);

        Category saved = categoryRepository.save(category);
        return categoryMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new EntityNotFoundException("Categoría no encontrada con ID: " + id);
        }
        if (subcategoryRepository.existsByCategoryId(id)) {
            throw new IllegalStateException("No se puede eliminar la categoría porque tiene subcategorías creadas por una o más tiendas");
        }

        for (CategoryDeleteValidator validator : deleteValidators) {
            validator.validateBeforeDelete(id); 
        }

        categoryRepository.deleteById(id);
    }

    @Override
    public List<CategoryDto> listAll() {
        return categoryRepository.findAll().stream()
                .map(categoryMapper::toDto)
                .collect(Collectors.toList());
    }

    private void validateCategoryName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría es obligatorio");
        }
    }

    private void validateCategoryNameUniqueness(String name, Long currentCategoryId) {
        if (currentCategoryId == null) {
            if (categoryRepository.existsByNameIgnoreCase(name.trim())) {
                throw new IllegalArgumentException("Ya existe una categoría con el nombre: " + name.trim());
            }
        } else {
            categoryRepository.findByNameIgnoreCase(name.trim())
                    .ifPresent(category -> {
                        if (!category.getId().equals(currentCategoryId)) {
                            throw new IllegalArgumentException("Ya existe una categoría con el nombre: " + name.trim());
                        }
                    });
        }
    }

}
