package com.sciencebot.pos.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.services.internal.entities.ServiceCategory;
import com.sciencebot.pos.services.internal.mappers.ServiceCategoryMapper;
import com.sciencebot.pos.services.internal.repositories.ServiceCategoryRepository;
import com.sciencebot.pos.services.internal.repositories.ServiceItemRepository;
import com.sciencebot.pos.services.internal.services.ServiceCategoryServiceImpl;
import com.sciencebot.pos.stores.StoreCategoryDto;
import com.sciencebot.pos.stores.StoreDto;
import com.sciencebot.pos.stores.StoreFacade;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServiceCategoryServiceImplTest {

    private static final Long STORE_ID = 7L;
    private static final Long BARBERSHOP = 10L;
    private static final Long BOUTIQUE = 11L;

    private final ServiceCategoryRepository categoryRepository = mock(ServiceCategoryRepository.class);
    private final ServiceItemRepository serviceRepository = mock(ServiceItemRepository.class);
    private final StoreFacade storeFacade = mock(StoreFacade.class);
    private ServiceCategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ServiceCategoryServiceImpl(categoryRepository, serviceRepository, new ServiceCategoryMapper(), storeFacade);
        TenantContext.setStoreId(STORE_ID);
        givenStoreOfCategory(BARBERSHOP);
        when(categoryRepository.save(any(ServiceCategory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void givenStoreOfCategory(Long storeCategoryId) {
        StoreDto store = mock(StoreDto.class);
        when(store.storeCategoryId()).thenReturn(storeCategoryId);
        when(storeFacade.getById(STORE_ID)).thenReturn(store);
    }

    private static ServiceCategory category(Long id, String name, Long... storeCategories) {
        ServiceCategory c = new ServiceCategory();
        c.setId(id);
        c.setName(name);
        c.setStoreCategoryIds(new java.util.HashSet<>(Set.of(storeCategories)));
        return c;
    }

    @Test
    void listForCurrentStore_OffersTheLinkedCategoriesAndTheGeneralOnes() {
        when(categoryRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(
                category(1L, "Barba", BARBERSHOP),
                category(2L, "Arreglos", BOUTIQUE),
                category(3L, "Otros")));

        List<String> names = service.listForCurrentStore().stream().map(ServiceCategoryDto::name).toList();

        assertEquals(List.of("Barba", "Otros"), names);
    }

    @Test
    void listForCurrentStore_StoreWithoutCategory_OnlySeesTheGeneralOnes() {
        givenStoreOfCategory(null);
        when(categoryRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(
                category(1L, "Barba", BARBERSHOP), category(3L, "Otros")));

        assertEquals(List.of("Otros"), service.listForCurrentStore().stream().map(ServiceCategoryDto::name).toList());
    }

    @Test
    void requireAvailable_CategoryOfAnotherStoreType_IsRefused() {
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category(2L, "Arreglos", BOUTIQUE)));

        assertThrows(IllegalArgumentException.class, () -> service.requireAvailableForCurrentStore(2L));
    }

    @Test
    void requireAvailable_LinkedGeneralOrNull_BehavesAsExpected() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "Barba", BARBERSHOP)));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category(3L, "Otros")));
        ServiceCategory inactive = category(4L, "Vieja", BARBERSHOP);
        inactive.setActive(false);
        when(categoryRepository.findById(4L)).thenReturn(Optional.of(inactive));

        assertDoesNotThrow(() -> service.requireAvailableForCurrentStore(1L));
        assertDoesNotThrow(() -> service.requireAvailableForCurrentStore(3L));
        assertThrows(IllegalArgumentException.class, () -> service.requireAvailableForCurrentStore(4L));
        assertThrows(IllegalArgumentException.class, () -> service.requireAvailableForCurrentStore(99L));
        assertThrows(IllegalArgumentException.class, () -> service.requireAvailableForCurrentStore(null));
    }

    @Test
    void create_LinksTheStoreCategoriesAndRejectsDuplicatesAndUnknownLinks() {
        when(storeFacade.listCategories()).thenReturn(List.of(
                new StoreCategoryDto(BARBERSHOP, "Barbería", null, true, "APPOINTMENTS", "BEAUTY", List.of("APPOINTMENTS"))));

        ServiceCategoryDto dto = service.create(new CreateServiceCategoryCommand(" Corte ", " ", List.of(BARBERSHOP)));

        assertEquals("Corte", dto.name());
        assertNull(dto.description());
        assertEquals(List.of(BARBERSHOP), dto.storeCategoryIds());

        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateServiceCategoryCommand("Corte", null, List.of(999L))));
        when(categoryRepository.existsByNameIgnoreCase("Corte")).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateServiceCategoryCommand("Corte", null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateServiceCategoryCommand(" ", null, null)));
    }

    @Test
    void update_UnknownCategory_IsNotFound() {
        when(categoryRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.update(9L, new CreateServiceCategoryCommand("X", null, null)));
    }

    @Test
    void deactivate_WithActiveServices_IsRefused_andWithoutThemDeactivates() {
        ServiceCategory c = category(1L, "Barba", BARBERSHOP);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(c));
        when(serviceRepository.existsByCategoryIdAndActiveTrue(1L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.deactivate(1L));
        assertTrue(c.isActive());

        when(serviceRepository.existsByCategoryIdAndActiveTrue(1L)).thenReturn(false);
        service.deactivate(1L);
        assertFalse(c.isActive());
    }
}
