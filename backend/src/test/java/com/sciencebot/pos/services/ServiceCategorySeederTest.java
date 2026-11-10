package com.sciencebot.pos.services;

import com.sciencebot.pos.services.internal.repositories.ServiceCategoryRepository;
import com.sciencebot.pos.services.internal.services.ServiceCategorySeeder;
import com.sciencebot.pos.stores.StoreCategoryDto;
import com.sciencebot.pos.stores.StoreFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServiceCategorySeederTest {

    private final ServiceCategoryRepository repository = mock(ServiceCategoryRepository.class);
    private final ServiceCategoryFacade facade = mock(ServiceCategoryFacade.class);
    private final StoreFacade storeFacade = mock(StoreFacade.class);
    private final ServiceCategorySeeder seeder = new ServiceCategorySeeder(repository, facade, storeFacade);

    private static StoreCategoryDto storeCategory(Long id, String name) {
        return new StoreCategoryDto(id, name, null, true, "APPOINTMENTS", "BEAUTY", List.of("APPOINTMENTS"));
    }

    @Test
    void seedsEachCategoryLinkedToItsStoreCategories_andTheGeneralOneWithoutLinks() {
        when(storeFacade.listCategories()).thenReturn(List.of(
                storeCategory(1L, "Barbería"), storeCategory(2L, "Peluquería"),
                storeCategory(3L, "Salón de uñas y pies"), storeCategory(4L, "Boutique")));

        seeder.run();

        ArgumentCaptor<CreateServiceCategoryCommand> captor = ArgumentCaptor.forClass(CreateServiceCategoryCommand.class);
        verify(facade, times(9)).create(captor.capture());
        CreateServiceCategoryCommand corte = captor.getAllValues().stream().filter(c -> c.name().equals("Corte")).findFirst().orElseThrow();
        assertEquals(List.of(1L, 2L), corte.storeCategoryIds());
        CreateServiceCategoryCommand otros = captor.getAllValues().stream().filter(c -> c.name().equals("Otros servicios")).findFirst().orElseThrow();
        assertTrue(otros.storeCategoryIds().isEmpty());
    }

    @Test
    void isIdempotent_andSkipsCategoriesWhoseStoreCategoriesDoNotExist() {
        when(storeFacade.listCategories()).thenReturn(List.of(storeCategory(1L, "Barbería")));
        when(repository.existsByNameIgnoreCase("Corte")).thenReturn(true);

        seeder.run();

        ArgumentCaptor<CreateServiceCategoryCommand> captor = ArgumentCaptor.forClass(CreateServiceCategoryCommand.class);
        verify(facade, atLeastOnce()).create(captor.capture());
        List<String> names = captor.getAllValues().stream().map(CreateServiceCategoryCommand::name).toList();
        assertFalse(names.contains("Corte"));          // already there
        assertFalse(names.contains("Peinado"));        // its store category (Peluquería) is missing: would be open to all
        assertTrue(names.contains("Barba"));
        assertTrue(names.contains("Otros servicios"));
        verify(facade, never()).create(argThat(c -> c == null));
    }
}
