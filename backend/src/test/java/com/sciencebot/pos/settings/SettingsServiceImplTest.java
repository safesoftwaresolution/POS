package com.sciencebot.pos.settings;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.settings.internal.entities.Setting;
import com.sciencebot.pos.settings.internal.repositories.SettingsRepository;
import com.sciencebot.pos.settings.internal.services.SettingsServiceImpl;
import com.sciencebot.pos.settings.internal.mappers.SettingsMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SettingsServiceImplTest {

    private static final Long STORE_ID = 1L;

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private SettingsMapper settingsMapper;

    @InjectMocks
    private SettingsServiceImpl settingsService;

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
    void getSettings_Success() {
        Setting setting = new Setting();
        setting.setId(STORE_ID);
        setting.setBusinessName("Mi Tienda");
        setting.setAddress("Calle Principal 123");
        setting.setPhone("555-0001");
        when(settingsRepository.findById(STORE_ID)).thenReturn(Optional.of(setting));

        SettingsDto expectedDto = new SettingsDto("Mi Tienda", "Calle Principal 123", "555-0001", "900111222-3", "info@mitienda.com", null);
        when(settingsMapper.toDto(any(Setting.class))).thenReturn(expectedDto);

        SettingsDto result = settingsService.getSettings();

        assertNotNull(result);
        assertEquals("Mi Tienda", result.businessName());
        verify(settingsRepository, times(1)).findById(STORE_ID);
    }

    @Test
    void getSettings_NotYetSeeded_CreatesDefaultForCurrentStore() {
        when(settingsRepository.findById(STORE_ID)).thenReturn(Optional.empty());
        when(settingsRepository.save(any(Setting.class))).thenAnswer(inv -> inv.getArgument(0));

        SettingsDto expectedDto = new SettingsDto("Mi Negocio", "Por definir", "Por definir", null, null, null);
        when(settingsMapper.toDto(any(Setting.class))).thenReturn(expectedDto);

        SettingsDto result = settingsService.getSettings();

        assertNotNull(result);
        verify(settingsRepository, times(1)).save(argThat(s -> STORE_ID.equals(s.getId())));
    }

    @Test
    void updateSettings_Success() {
        Setting setting = new Setting();
        setting.setId(STORE_ID);
        setting.setBusinessName("Mi Tienda");
        setting.setAddress("Calle Principal 123");
        setting.setPhone("555-0001");
        when(settingsRepository.findById(STORE_ID)).thenReturn(Optional.of(setting));

        UpdateSettingsCommand command = new UpdateSettingsCommand("Nuevo Nombre", "Nueva Calle", "555-0002", "1-2", "a@a.com", null);

        Setting saved = new Setting();
        saved.setId(STORE_ID);
        saved.setBusinessName("Nuevo Nombre");
        when(settingsRepository.save(any(Setting.class))).thenReturn(saved);

        SettingsDto expectedDto = new SettingsDto("Nuevo Nombre", "Nueva Calle", "555-0002", "1-2", "a@a.com", null);
        when(settingsMapper.toDto(any(Setting.class))).thenReturn(expectedDto);

        SettingsDto result = settingsService.updateSettings(command);

        assertNotNull(result);
        assertEquals("Nuevo Nombre", result.businessName());
        verify(settingsRepository, times(1)).save(any(Setting.class));
    }

    @Test
    void seedSettings_ForAnExplicitStore_CopiesTheStoreDataWithoutATenantInContext() {
        TenantContext.clear();
        when(settingsRepository.existsById(9L)).thenReturn(false);

        settingsService.seedSettings(9L, new SettingsDto("Tienda 1", "Calle 1", "3001234567", "900123", "tienda1@test.com", null));

        verify(settingsRepository).save(argThat(s -> Long.valueOf(9L).equals(s.getId())
                && "Tienda 1".equals(s.getBusinessName()) && "Calle 1".equals(s.getAddress())
                && "3001234567".equals(s.getPhone()) && "900123".equals(s.getTaxId())
                && "tienda1@test.com".equals(s.getEmail())));
    }

    @Test
    void seedSettings_ForAnExplicitStore_FillsTheRequiredBlanksAndNeverOverwrites() {
        when(settingsRepository.existsById(9L)).thenReturn(false);
        settingsService.seedSettings(9L, new SettingsDto("Tienda 1", " ", null, null, "tienda1@test.com", null));
        verify(settingsRepository).save(argThat(s -> "Por definir".equals(s.getAddress()) && "Por definir".equals(s.getPhone())));

        when(settingsRepository.existsById(10L)).thenReturn(true);
        settingsService.seedSettings(10L, new SettingsDto("Otra", "Calle 2", "300", null, null, null));
        verify(settingsRepository, never()).save(argThat(s -> Long.valueOf(10L).equals(s.getId())));
    }
}
