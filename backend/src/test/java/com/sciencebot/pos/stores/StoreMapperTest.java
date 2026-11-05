package com.sciencebot.pos.stores;

import com.sciencebot.pos.stores.internal.entities.StoreCategoryEntity;
import com.sciencebot.pos.stores.internal.entities.StoreEntity;
import com.sciencebot.pos.stores.internal.mappers.StoreMapper;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreMapperTest {

    private final StoreMapper mapper = new StoreMapper();

    private static StoreCategoryEntity category(String name, Set<StoreTemplate> templates, StoreTheme theme) {
        StoreCategoryEntity c = new StoreCategoryEntity();
        c.setId(5L);
        c.setName(name);
        c.setTemplates(new LinkedHashSet<>(templates));
        c.setTheme(theme);
        return c;
    }

    private static StoreCategoryEntity category(String name, StoreTemplate template) {
        return category(name, EnumSet.of(template), StoreTheme.DEFAULT);
    }

    private static StoreEntity storeWithExtras(StoreTemplate... extras) {
        StoreEntity store = new StoreEntity();
        store.setStoreCategoryId(5L);
        store.setExtraTemplates(new LinkedHashSet<>(List.of(extras)));
        return store;
    }

    @Test
    void storeTemplatesAreTheOnesOfItsCategory() {
        StoreDto dto = mapper.toDto(storeWithExtras(), category("Barbería", StoreTemplate.APPOINTMENTS));

        assertEquals("Barbería", dto.categoryName());
        assertEquals(List.of("APPOINTMENTS"), dto.templates());
        assertEquals("APPOINTMENTS", dto.template());
        assertEquals(List.of(), dto.extraTemplates());
    }

    @Test
    void storeTemplatesAreTheCategoryOnesPlusItsExtras_inStableOrderWithoutDuplicates() {
        StoreCategoryEntity boutique = category("Boutique", EnumSet.of(StoreTemplate.RETAIL), StoreTheme.FASHION);

        StoreDto dto = mapper.toDto(storeWithExtras(StoreTemplate.APPOINTMENTS, StoreTemplate.RETAIL), boutique);

        assertEquals(List.of("RETAIL", "APPOINTMENTS"), dto.templates());
        assertEquals(List.of("RETAIL", "APPOINTMENTS"), dto.extraTemplates(), "los extras se muestran ordenados, tal cual se guardaron");
        assertEquals("APPOINTMENTS", dto.template(), "la principal es APPOINTMENTS > FOOD > RETAIL");
    }

    @Test
    void anExtraCanGiveTemplatesToAStoreWhoseCategoryHasOnlyRetail() {
        StoreDto dto = mapper.toDto(storeWithExtras(StoreTemplate.FOOD), category("Otros", StoreTemplate.RETAIL));

        assertEquals(List.of("RETAIL", "FOOD"), dto.templates());
        assertEquals("FOOD", dto.template());
    }

    @Test
    void storeWithoutCategoryAndWithoutExtrasFallsBackToRetail() {
        StoreDto dto = mapper.toDto(new StoreEntity(), null);

        assertNull(dto.categoryName());
        assertEquals(List.of("RETAIL"), dto.templates());
        assertEquals("RETAIL", dto.template());
        assertEquals("DEFAULT", dto.theme());
    }

    @Test
    void storeWithoutCategoryStillGetsItsExtras() {
        StoreEntity store = new StoreEntity();
        store.setExtraTemplates(new LinkedHashSet<>(List.of(StoreTemplate.APPOINTMENTS)));

        assertEquals(List.of("APPOINTMENTS"), mapper.toDto(store, null).templates());
    }

    @Test
    void storeThemeIsDerivedFromItsCategoryAndDefaultsWithoutOne() {
        StoreCategoryEntity barber = category("Barbería", EnumSet.of(StoreTemplate.APPOINTMENTS), StoreTheme.BEAUTY);

        assertEquals("BEAUTY", mapper.toDto(storeWithExtras(), barber).theme());
        assertEquals("DEFAULT", mapper.toDto(new StoreEntity(), null).theme());
    }

    @Test
    void categoryDtoExposesItsTemplatesPrimaryTemplateAndTheme() {
        StoreCategoryDto dto = mapper.toCategoryDto(
                category("Boutique", EnumSet.of(StoreTemplate.RETAIL, StoreTemplate.APPOINTMENTS), StoreTheme.FASHION));

        assertEquals(List.of("RETAIL", "APPOINTMENTS"), dto.templates());
        assertEquals("APPOINTMENTS", dto.template());
        assertEquals("FASHION", dto.theme());
    }
}
