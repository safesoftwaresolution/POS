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
    void doesNotRecreateExistingCategories() {
        StoreCategoryRepository repository = mock(StoreCategoryRepository.class);
        StoreFacade facade = mock(StoreFacade.class);
        when(repository.existsByNameIgnoreCase(anyString())).thenReturn(true);

        new StoreCategorySeeder(repository, facade).run();

        verify(facade, never()).createCategory(any());
    }
}
