package com.sciencebot.pos.categories;

/**
 * SPI implementada por products: cuenta productos activos asociados a una subcategoría.
 * Nombre de método distinto de CategoryProductCountProvider a propósito (ver
 * SubcategoryDeleteValidator: misma clase implementa ambas interfaces).
 */
public interface SubcategoryProductCountProvider {
    int getSubcategoryProductCount(Long subcategoryId);
}
