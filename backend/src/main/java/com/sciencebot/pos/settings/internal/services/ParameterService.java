package com.sciencebot.pos.settings.internal.services;

import com.sciencebot.pos.settings.*;
import java.util.List;
import java.util.Optional;

/**
 * Servicio interno del módulo settings para la gestión de catálogos dinámicos.
 * El {@code ParameterController} lo usa para el CRUD completo; la parte de consulta
 * entre módulos se expone además vía {@link com.sciencebot.pos.settings.SettingsFacade}.
 */
public interface ParameterService {

    List<ParameterTopicDto> getAllTopics();

    ParameterTopicDto createTopic(CreateParameterTopicCommand command);

    /** Todos los valores del tema (incluye inactivos) — vista de administración. */
    List<ParameterValueDto> getValuesByTopic(String topicCode);

    /** Solo valores activos del tema, ordenados — consumo (dropdowns / cross-módulo). */
    List<ParameterValueDto> getActiveValuesByTopic(String topicCode);

    Optional<ParameterValueDto> getValueByTopicAndCode(String topicCode, String valueCode);

    ParameterValueDto addValue(String topicCode, CreateParameterValueCommand command);

    ParameterValueDto updateValue(Long valueId, UpdateParameterValueCommand command);

    /** Borrado lógico: desactiva el valor (active = false). */
    void deactivateValue(Long valueId);
}
