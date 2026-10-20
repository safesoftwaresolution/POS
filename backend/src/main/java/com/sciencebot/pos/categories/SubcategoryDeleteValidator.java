package com.sciencebot.pos.categories;

/**
 * SPI implementada por products: bloquea el borrado de una subcategoría con productos
 * activos asociados. Nombre de método distinto de CategoryDeleteValidator a propósito:
 * ambas interfaces las implementa la misma clase (ProductServiceImpl) y Java no permite
 * dos métodos con igual firma (Long) aunque vengan de interfaces distintas.
 */
public interface SubcategoryDeleteValidator {
    void validateSubcategoryBeforeDelete(Long subcategoryId);
}
