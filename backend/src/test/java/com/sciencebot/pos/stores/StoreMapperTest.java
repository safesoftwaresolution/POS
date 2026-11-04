package com.sciencebot.pos.stores;

import com.sciencebot.pos.stores.internal.entities.StoreCategoryEntity;
import com.sciencebot.pos.stores.internal.entities.StoreEntity;
import com.sciencebot.pos.stores.internal.mappers.StoreMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreMapperTest {

    private final StoreMapper mapper = new StoreMapper();

    private static StoreCategoryEntity category(String name, StoreTemplate template) {
        StoreCategoryEntity c = new StoreCategoryEntity();
        c.setId(5L);
        c.setName(name);
        c.setTemplate(template);
        return c;
    }

    @Test
    void storeTemplateIsDerivedFromItsCategory() {
        StoreEntity store = new StoreEntity();
        store.setStoreCategoryId(5L);

        StoreDto dto = mapper.toDto(store, category("Barbería", StoreTemplate.APPOINTMENTS));

        assertEquals("Barbería", dto.categoryName());
        assertEquals("APPOINTMENTS", dto.template());
    }

    @Test
    void storeWithoutCategoryFallsBackToRetail() {
        StoreDto dto = mapper.toDto(new StoreEntity(), null);

        assertNull(dto.categoryName());
        assertEquals("RETAIL", dto.template());
    }

    @Test
    void categoryDtoExposesItsTemplate() {
        assertEquals("FOOD", mapper.toCategoryDto(category("Restaurante", StoreTemplate.FOOD)).template());
    }
}
