package com.sciencebot.pos.settings;

import java.util.List;
import java.util.Optional;

public interface SettingsFacade {
    SettingsDto getSettings();
    SettingsDto updateSettings(UpdateSettingsCommand command);
    void seedSettings(SettingsDto defaultSettings);
    /**
     * Igual que {@link #seedSettings(SettingsDto)} pero para un local explícito, sin depender del local de la petición
     * (p. ej. al registrar el local, cuando el token aún no lo trae). No sobrescribe una configuración existente;
     * dirección y teléfono vacíos quedan "Por definir".
     */
    void seedSettings(Long storeId, SettingsDto defaultSettings);

    // --- Catálogos dinámicos (consulta entre módulos) ---

    /** Valores activos de un tema, ordenados por {@code sortOrder}. Lista vacía si el tema no existe. */
    List<ParameterValueDto> getActiveValuesByTopic(String topicCode);

    /** Valor concreto (activo o no) por tema y código. */
    Optional<ParameterValueDto> getValueByTopicAndCode(String topicCode, String valueCode);
}
