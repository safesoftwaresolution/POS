package com.sciencebot.pos.settings.internal.repositories;

import com.sciencebot.pos.settings.internal.entities.ParameterValue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ParameterValueRepository extends JpaRepository<ParameterValue, Long> {

    /** Todos los valores de un tema (incluye inactivos), para la vista de administración. */
    List<ParameterValue> findByTopic_IdOrderBySortOrderAscIdAsc(Long topicId);

    /** Solo valores activos de un tema, ordenados, para consumo (dropdowns, cross-módulo). */
    List<ParameterValue> findByTopic_CodeAndActiveTrueOrderBySortOrderAscIdAsc(String topicCode);

    Optional<ParameterValue> findByTopic_CodeAndCode(String topicCode, String code);

    boolean existsByTopic_IdAndCode(Long topicId, String code);
}
