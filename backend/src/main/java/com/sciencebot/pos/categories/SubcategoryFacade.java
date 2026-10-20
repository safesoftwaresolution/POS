package com.sciencebot.pos.categories;

import java.util.List;
import java.util.Optional;

public interface SubcategoryFacade {
    Optional<SubcategoryDto> getById(Long id);
    List<SubcategoryDto> listByCategory(Long categoryId);
    List<SubcategoryDto> listAll();
    SubcategoryDto createSubcategory(CreateSubcategoryCommand command);
    SubcategoryDto updateSubcategory(Long id, UpdateSubcategoryCommand command);
    void deleteSubcategory(Long id);
}
