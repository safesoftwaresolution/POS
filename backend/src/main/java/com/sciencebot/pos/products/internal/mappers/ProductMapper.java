package com.sciencebot.pos.products.internal.mappers;

import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.categories.SubcategoryFacade;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.internal.entities.Product;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class ProductMapper {

    private final CategoryFacade categoryFacade;
    private final SubcategoryFacade subcategoryFacade;

    public ProductMapper(
            @org.springframework.context.annotation.Lazy CategoryFacade categoryFacade,
            @org.springframework.context.annotation.Lazy SubcategoryFacade subcategoryFacade) {
        this.categoryFacade = categoryFacade;
        this.subcategoryFacade = subcategoryFacade;
    }


    public ProductDto toDto(Product product) {
        return toDto(product, null);
    }

    /** {@code nextExpiration}: vencimiento del lote más próximo con unidades (se deriva de los lotes), o null. */
    public ProductDto toDto(Product product, LocalDate nextExpiration) {
        if (product == null) {
            return null;
        }

        String categoryName = categoryFacade.getById(product.getCategoryId())
                .map(category -> category.name())
                .orElse("Sin Categoría");

        String subcategoryName = product.getSubcategoryId() != null
                ? subcategoryFacade.getById(product.getSubcategoryId()).map(sub -> sub.name()).orElse(null)
                : null;

        return new ProductDto(
                product.getId(),
                product.getInternalCode(),
                product.getBarcode(),
                product.getName(),
                categoryName,
                product.getSubcategoryId(),
                subcategoryName,
                product.getPurchasePrice(),
                product.getSalePrice(),
                product.getQuantityAvailable(),
                product.getMinStock(),
                product.isActive(),
                product.getImageUrl(),
                product.getTaxRate(),
                product.getUnspscCode(),
                product.getCategoryId(),
                product.getDescription(),
                nextExpiration
        );
    }
}
