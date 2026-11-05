package com.sciencebot.pos.stores;

import com.sciencebot.pos.stores.internal.repositories.StoreCategoryRepository;
import com.sciencebot.pos.stores.internal.services.StoreCategorySeeder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StoreCategorySeederTest {

    @Test
    void seedsTheBeautyCategoriesWithTheAppointmentsTemplateAndFoodWithFood() {
        StoreCategoryRepository repository = mock(StoreCategoryRepository.class);
        StoreFacade facade = mock(StoreFacade.class);
        when(repository.existsByNameIgnoreCase(anyString())).thenReturn(false);

        new StoreCategorySeeder(repository, facade).run();

        ArgumentCaptor<CreateStoreCategoryCommand> captor = ArgumentCaptor.forClass(CreateStoreCategoryCommand.class);
        verify(facade, atLeastOnce()).createCategory(captor.capture());
        Map<String, String> templates = captor.getAllValues().stream()
                .collect(Collectors.toMap(CreateStoreCategoryCommand::name, CreateStoreCategoryCommand::template));

        for (String name : List.of("Barbería", "Peluquería", "Salón de uñas y pies", "Boutique")) {
            assertEquals("APPOINTMENTS", templates.get(name), name);
        }
        assertEquals("FOOD", templates.get("Restaurante / Cafetería"));
        assertEquals("RETAIL", templates.get("Supermercado"));
    }

    @Test
    void everyBaseCategoryGetsTheThemeOfItsBusinessType() {
        StoreCategoryRepository repository = mock(StoreCategoryRepository.class);
        StoreFacade facade = mock(StoreFacade.class);
        when(repository.existsByNameIgnoreCase(anyString())).thenReturn(false);

        new StoreCategorySeeder(repository, facade).run();

        ArgumentCaptor<CreateStoreCategoryCommand> captor = ArgumentCaptor.forClass(CreateStoreCategoryCommand.class);
        verify(facade, atLeastOnce()).createCategory(captor.capture());
        Map<String, String> themes = captor.getAllValues().stream()
                .collect(Collectors.toMap(CreateStoreCategoryCommand::name, CreateStoreCategoryCommand::theme));

        assertEquals("GROCERY", themes.get("Tienda de barrio / Minimercado"));
        assertEquals("GROCERY", themes.get("Supermercado"));
        assertEquals("PHARMACY", themes.get("Droguería / Farmacia"));
        assertEquals("BAKERY", themes.get("Panadería"));
        assertEquals("STATIONERY", themes.get("Papelería"));
        assertEquals("HARDWARE", themes.get("Ferretería"));
        assertEquals("FOOD", themes.get("Restaurante / Cafetería"));
        assertEquals("LIQUOR", themes.get("Licorera"));
        assertEquals("PETS", themes.get("Tienda de mascotas"));
        assertEquals("BEAUTY", themes.get("Barbería"));
        assertEquals("BEAUTY", themes.get("Peluquería"));
        assertEquals("BEAUTY", themes.get("Salón de uñas y pies"));
        assertEquals("FASHION", themes.get("Boutique"));
        assertEquals("DEFAULT", themes.get("Otros"));
    }

    @Test
    void doesNotRecreateExistingCategories() {
        StoreCategoryRepository repository = mock(StoreCategoryRepository.class);
        StoreFacade facade = mock(StoreFacade.class);
        when(repository.existsByNameIgnoreCase(anyString())).thenReturn(true);

        new StoreCategorySeeder(repository, facade).run();

        verify(facade, never()).createCategory(any());
    }
}
