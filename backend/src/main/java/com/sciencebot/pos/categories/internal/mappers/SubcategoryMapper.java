package com.sciencebot.pos.categories.internal.mappers;

import com.sciencebot.pos.categories.SubcategoryDto;
import com.sciencebot.pos.categories.SubcategoryProductCountProvider;
import com.sciencebot.pos.categories.internal.entities.Category;
import com.sciencebot.pos.categories.internal.entities.Subcategory;
import com.sciencebot.pos.categories.internal.repositories.CategoryRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubcategoryMapper {

    private final CategoryRepository categoryRepository;
    private final List<SubcategoryProductCountProvider> countProviders;

    public SubcategoryMapper(CategoryRepository categoryRepository,
                              @Lazy List<SubcategoryProductCountProvider> countProviders) {
        this.categoryRepository = categoryRepository;
        this.countProviders = countProviders;
    }

    public SubcategoryDto toDto(Subcategory subcategory) {
        if (subcategory == null) {
            return null;
        }

        String categoryName = categoryRepository.findById(subcategory.getCategoryId())
                .map(Category::getName)
                .orElse("Sin categoría");

        int productCount = 0;
        if (countProviders != null) {
            for (SubcategoryProductCountProvider provider : countProviders) {
                productCount += provider.getSubcategoryProductCount(subcategory.getId());
            }
        }

        return new SubcategoryDto(
                subcategory.getId(),
                subcategory.getCategoryId(),
                categoryName,
                subcategory.getName(),
                subcategory.getDescription(),
                productCount
        );
    }
}
