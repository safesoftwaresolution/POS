package com.sciencebot.pos.settings.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.settings.*;
import com.sciencebot.pos.settings.internal.entities.Setting;
import com.sciencebot.pos.settings.internal.repositories.SettingsRepository;
import com.sciencebot.pos.settings.internal.mappers.SettingsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SettingsServiceImpl implements SettingsFacade {

    private static final String DEFAULT_BUSINESS_NAME = "Mi Negocio";
    private static final String DEFAULT_ADDRESS = "Por definir";
    private static final String DEFAULT_PHONE = "Por definir";

    private final SettingsRepository settingsRepository;
    private final SettingsMapper settingsMapper;
    private final ParameterService parameterService;

    public SettingsServiceImpl(SettingsRepository settingsRepository, SettingsMapper settingsMapper,
                               ParameterService parameterService) {
        this.settingsRepository = settingsRepository;
        this.settingsMapper = settingsMapper;
        this.parameterService = parameterService;
    }

    @Override
    public SettingsDto getSettings() {
        Setting setting = settingsRepository.findById(requireCurrentStoreId())
                .orElseGet(this::createDefaultSettingsForCurrentStore);
        return settingsMapper.toDto(setting);
    }

    /**
     * Un local recien creado (auto-registro) todavia no tiene fila en 'settings' (nadie la
     * siembra en el flujo de onboarding). En vez de fallar con 404 en la primera consulta, se
     * inicializa aqui con valores por defecto editables desde el propio módulo de Ajustes.
     */
    @Transactional
    protected Setting createDefaultSettingsForCurrentStore() {
        Setting setting = new Setting();
        setting.setId(requireCurrentStoreId());
        setting.setBusinessName(DEFAULT_BUSINESS_NAME);
        setting.setAddress(DEFAULT_ADDRESS);
        setting.setPhone(DEFAULT_PHONE);
        return settingsRepository.save(setting);
    }

    @Override
    @Transactional
    public SettingsDto updateSettings(UpdateSettingsCommand command) {
        Setting setting = settingsRepository.findById(requireCurrentStoreId())
                .orElseGet(this::createDefaultSettingsForCurrentStore);

        if (command.businessName() == null || command.businessName().isBlank()) {
            throw new IllegalArgumentException("El nombre del negocio es obligatorio");
        }
        if (command.address() == null || command.address().isBlank()) {
            throw new IllegalArgumentException("La dirección del negocio es obligatoria");
        }
        if (command.phone() == null || command.phone().isBlank()) {
            throw new IllegalArgumentException("El teléfono del negocio es obligatorio");
        }

        setting.setBusinessName(command.businessName().trim());
        setting.setAddress(command.address().trim());
        setting.setPhone(command.phone().trim());
        setting.setTaxId(command.taxId() != null ? command.taxId().trim() : null);
        setting.setEmail(command.email() != null ? command.email().trim() : null);
        setting.setLogoUrl(command.logoUrl() != null ? command.logoUrl().trim() : null);

        Setting saved = settingsRepository.save(setting);
        return settingsMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void seedSettings(SettingsDto defaultSettings) {
        Long storeId = requireCurrentStoreId();
        if (!settingsRepository.existsById(storeId)) {
            Setting setting = new Setting();
            setting.setId(storeId);
            setting.setBusinessName(defaultSettings.businessName());
            setting.setAddress(defaultSettings.address());
            setting.setPhone(defaultSettings.phone());
            setting.setTaxId(defaultSettings.taxId());
            setting.setEmail(defaultSettings.email());
            setting.setLogoUrl(defaultSettings.logoUrl());
            settingsRepository.save(setting);
        }
    }

    // --- Catálogos dinámicos: delegación al servicio de parámetros del módulo ---

    @Override
    public List<ParameterValueDto> getActiveValuesByTopic(String topicCode) {
        return parameterService.getActiveValuesByTopic(topicCode);
    }

    @Override
    public Optional<ParameterValueDto> getValueByTopicAndCode(String topicCode, String valueCode) {
        return parameterService.getValueByTopicAndCode(topicCode, valueCode);
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
