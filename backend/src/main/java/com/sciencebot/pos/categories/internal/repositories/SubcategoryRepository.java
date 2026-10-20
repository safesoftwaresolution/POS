package com.sciencebot.pos.categories.internal.repositories;

import com.sciencebot.pos.categories.internal.entities.Subcategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {
    List<Subcategory> findByStoreIdAndCategoryId(Long storeId, Long categoryId);
    List<Subcategory> findByStoreId(Long storeId);
    Optional<Subcategory> findByStoreIdAndCategoryIdAndNameIgnoreCase(Long storeId, Long categoryId, String name);
    boolean existsByStoreIdAndCategoryIdAndNameIgnoreCase(Long storeId, Long categoryId, String name);
    boolean existsByCategoryId(Long categoryId);
}
