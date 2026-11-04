package com.sciencebot.pos.services;

import com.sciencebot.pos.categories.CategoryDto;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.services.internal.entities.ServiceItem;
import com.sciencebot.pos.services.internal.mappers.ServiceMapper;
import com.sciencebot.pos.services.internal.repositories.ServiceItemRepository;
import com.sciencebot.pos.services.internal.services.ServiceCatalogServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ServiceCatalogServiceImplTest {

    private static final Long STORE_ID = 7L;

    private ServiceItemRepository repository;
    private CategoryFacade categoryFacade;
    private ServiceCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(ServiceItemRepository.class);
        categoryFacade = mock(CategoryFacade.class);
        service = new ServiceCatalogServiceImpl(repository, new ServiceMapper(categoryFacade), categoryFacade);
        when(categoryFacade.getById(3L)).thenReturn(Optional.of(mock(CategoryDto.class)));
        when(repository.save(any(ServiceItem.class))).thenAnswer(inv -> inv.getArgument(0));
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CreateServiceCommand command(String code, BigDecimal price) {
        return new CreateServiceCommand(code, "Masaje 60 min", " Relajante ", 3L, price, null, "90111500", null, 60);
    }

    @Test
    void createService_AppliesDefaultsAndStoreScope() {
        ServiceDto dto = service.createService(command(" SRV-1 ", BigDecimal.valueOf(120000)));

        assertEquals("SRV-1", dto.code());
        assertEquals("Relajante", dto.description());
        assertEquals(0, dto.taxRate().compareTo(BigDecimal.ZERO));
        assertEquals("94", dto.unitMeasureCode());
        assertTrue(dto.active());
        verify(repository).save(argThat(s -> s.getStoreId().equals(STORE_ID)));
    }

    @Test
    void createService_DuplicateCodeInStore_Throws() {
        when(repository.existsByStoreIdAndCode(STORE_ID, "SRV-1")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createService(command("SRV-1", BigDecimal.TEN)));
        verify(repository, never()).save(any());
    }

    @Test
    void createService_InvalidInput_Throws() {
        assertThrows(IllegalArgumentException.class, () -> service.createService(command("", BigDecimal.TEN)));
        assertThrows(IllegalArgumentException.class, () -> service.createService(command("A", BigDecimal.ZERO)));
        assertThrows(IllegalArgumentException.class, () -> service.createService(command("A", null)));
        assertThrows(IllegalArgumentException.class, () -> service.createService(
                new CreateServiceCommand("A", "x", null, 99L, BigDecimal.TEN, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.createService(
                new CreateServiceCommand("A", "x", null, 3L, BigDecimal.TEN, BigDecimal.valueOf(120), null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> service.createService(
                new CreateServiceCommand("A", "x", null, 3L, BigDecimal.TEN, null, null, null, 0)));
    }

    @Test
    void withoutStoreInContext_Throws() {
        TenantContext.clear();

        assertThrows(IllegalStateException.class, () -> service.getById(1L));
        assertThrows(IllegalStateException.class, () -> service.createService(command("A", BigDecimal.TEN)));
    }

    @Test
    void getById_ServiceOfAnotherStore_IsEmpty() {
        when(repository.findByIdAndStoreId(5L, STORE_ID)).thenReturn(Optional.empty());

        assertTrue(service.getById(5L).isEmpty());
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void updateService_OnlyChangesProvidedFields() {
        ServiceItem existing = new ServiceItem();
        existing.setId(5L);
        existing.setStoreId(STORE_ID);
        existing.setCode("SRV-1");
        existing.setName("Masaje");
        existing.setCategoryId(3L);
        existing.setPrice(BigDecimal.valueOf(100));
        existing.setTaxRate(BigDecimal.valueOf(19));
        when(repository.findByIdAndStoreId(5L, STORE_ID)).thenReturn(Optional.of(existing));

        ServiceDto dto = service.updateService(5L, new UpdateServiceCommand(null, null, null,
                BigDecimal.valueOf(150), null, null, null, null));

        assertEquals("Masaje", dto.name());
        assertEquals(0, dto.price().compareTo(BigDecimal.valueOf(150)));
        assertEquals(0, dto.taxRate().compareTo(BigDecimal.valueOf(19)));
    }

    @Test
    void deleteService_IsSoftDelete() {
        ServiceItem existing = new ServiceItem();
        existing.setStoreId(STORE_ID);
        when(repository.findByIdAndStoreId(5L, STORE_ID)).thenReturn(Optional.of(existing));

        service.deleteService(5L);

        assertFalse(existing.isActive());
        verify(repository, never()).delete(any(ServiceItem.class));
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void changeStatus_UnknownService_ThrowsNotFound() {
        when(repository.findByIdAndStoreId(9L, STORE_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.changeStatus(9L, true));
    }

    @Test
    void validateBeforeDelete_CategoryWithActiveServices_Throws() {
        when(repository.existsByCategoryIdAndActiveTrue(3L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.validateBeforeDelete(3L));
    }
}
