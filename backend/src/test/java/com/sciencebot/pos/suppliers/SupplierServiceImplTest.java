package com.sciencebot.pos.suppliers;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.suppliers.internal.entities.Supplier;
import com.sciencebot.pos.suppliers.internal.repositories.SupplierRepository;
import com.sciencebot.pos.suppliers.internal.services.SupplierServiceImpl;
import com.sciencebot.pos.suppliers.internal.mappers.SupplierMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupplierServiceImplTest {

    private static final Long STORE_ID = 1L;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierMapper supplierMapper;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setStoreId(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createSupplier_Success() {
        CreateSupplierCommand command = new CreateSupplierCommand("Distribuidora ABC", "900123456-1", "Pedro", "abc@mail.com", "555-0200", "Calle A");
        when(supplierRepository.existsByStoreIdAndTaxId(STORE_ID, "900123456-1")).thenReturn(false);

        Supplier saved = new Supplier();
        saved.setId(1L);
        saved.setStoreId(STORE_ID);
        saved.setCompanyName("Distribuidora ABC");
        saved.setTaxId("900123456-1");
        when(supplierRepository.save(any(Supplier.class))).thenReturn(saved);

        SupplierDto expectedDto = new SupplierDto(1L, "Distribuidora ABC", "900123456-1", "Pedro", "abc@mail.com", "555-0200", "Calle A", true);
        when(supplierMapper.toDto(any(Supplier.class))).thenReturn(expectedDto);

        SupplierDto result = supplierService.createSupplier(command);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("900123456-1", result.taxId());
        verify(supplierRepository, times(1)).save(any(Supplier.class));
    }

    @Test
    void createSupplier_DuplicateTaxId_ThrowsException() {
        CreateSupplierCommand command = new CreateSupplierCommand("Distribuidora ABC", "900123456-1", "Pedro", "abc@mail.com", "555-0200", "Calle A");
        when(supplierRepository.existsByStoreIdAndTaxId(STORE_ID, "900123456-1")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> supplierService.createSupplier(command));
        assertTrue(ex.getMessage().contains("Ya existe"));
    }
}
