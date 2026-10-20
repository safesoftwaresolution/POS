package com.sciencebot.pos.settings.internal.services;

import com.sciencebot.pos.settings.*;
import com.sciencebot.pos.settings.internal.entities.ParameterTopic;
import com.sciencebot.pos.settings.internal.entities.ParameterValue;
import com.sciencebot.pos.settings.internal.mappers.ParameterMapper;
import com.sciencebot.pos.settings.internal.repositories.ParameterTopicRepository;
import com.sciencebot.pos.settings.internal.repositories.ParameterValueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ParameterServiceImpl implements ParameterService {

    private final ParameterTopicRepository topicRepository;
    private final ParameterValueRepository valueRepository;
    private final ParameterMapper mapper;

    public ParameterServiceImpl(
            ParameterTopicRepository topicRepository,
            ParameterValueRepository valueRepository,
            ParameterMapper mapper
    ) {
        this.topicRepository = topicRepository;
        this.valueRepository = valueRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ParameterTopicDto> getAllTopics() {
        return topicRepository.findAllByOrderByNameAsc().stream()
                .map(mapper::toTopicDto)
                .toList();
    }

    @Override
    @Transactional
    public ParameterTopicDto createTopic(CreateParameterTopicCommand command) {
        String code = normalizeCode(command.code(), "El código del tema es obligatorio");
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("El nombre del tema es obligatorio");
        }
        if (topicRepository.existsByCode(code)) {
            throw new IllegalStateException("Ya existe un tema con el código: " + code);
        }

        ParameterTopic topic = new ParameterTopic();
        topic.setCode(code);
        topic.setName(command.name().trim());
        topic.setDescription(command.description() != null ? command.description().trim() : null);
        // Los temas creados vía API son de usuario; los del sistema se siembran por migración.
        topic.setSystem(false);

        return mapper.toTopicDto(topicRepository.save(topic));
    }

    @Override
    public List<ParameterValueDto> getValuesByTopic(String topicCode) {
        ParameterTopic topic = requireTopic(topicCode);
        return valueRepository.findByTopic_IdOrderBySortOrderAscIdAsc(topic.getId()).stream()
                .map(mapper::toValueDto)
                .toList();
    }

    @Override
    public List<ParameterValueDto> getActiveValuesByTopic(String topicCode) {
        if (topicCode == null || topicCode.isBlank()) {
            return List.of();
        }
        return valueRepository
                .findByTopic_CodeAndActiveTrueOrderBySortOrderAscIdAsc(topicCode.trim().toUpperCase())
                .stream()
                .map(mapper::toValueDto)
                .toList();
    }

    @Override
    public Optional<ParameterValueDto> getValueByTopicAndCode(String topicCode, String valueCode) {
        if (topicCode == null || topicCode.isBlank() || valueCode == null || valueCode.isBlank()) {
            return Optional.empty();
        }
        return valueRepository
                .findByTopic_CodeAndCode(topicCode.trim().toUpperCase(), valueCode.trim().toUpperCase())
                .map(mapper::toValueDto);
    }

    @Override
    @Transactional
    public ParameterValueDto addValue(String topicCode, CreateParameterValueCommand command) {
        ParameterTopic topic = requireTopic(topicCode);
        String code = normalizeCode(command.code(), "El código del valor es obligatorio");
        if (command.label() == null || command.label().isBlank()) {
            throw new IllegalArgumentException("La etiqueta del valor es obligatoria");
        }
        if (valueRepository.existsByTopic_IdAndCode(topic.getId(), code)) {
            throw new IllegalStateException("Ya existe un valor con el código '" + code + "' en el tema " + topic.getCode());
        }

        ParameterValue value = new ParameterValue();
        value.setTopic(topic);
        value.setCode(code);
        value.setLabel(command.label().trim());
        value.setExtraValue(command.extraValue() != null ? command.extraValue().trim() : null);
        value.setSortOrder(command.sortOrder() != null ? command.sortOrder() : 0);
        value.setActive(true);

        return mapper.toValueDto(valueRepository.save(value));
    }

    @Override
    @Transactional
    public ParameterValueDto updateValue(Long valueId, UpdateParameterValueCommand command) {
        ParameterValue value = valueRepository.findById(valueId)
                .orElseThrow(() -> new EntityNotFoundException("Valor de parámetro no encontrado con ID: " + valueId));

        if (command.label() != null) {
            if (command.label().isBlank()) {
                throw new IllegalArgumentException("La etiqueta del valor no puede estar vacía");
            }
            value.setLabel(command.label().trim());
        }
        if (command.extraValue() != null) {
            value.setExtraValue(command.extraValue().isBlank() ? null : command.extraValue().trim());
        }
        if (command.sortOrder() != null) {
            value.setSortOrder(command.sortOrder());
        }
        if (command.active() != null) {
            value.setActive(command.active());
        }

        return mapper.toValueDto(valueRepository.save(value));
    }

    @Override
    @Transactional
    public void deactivateValue(Long valueId) {
        ParameterValue value = valueRepository.findById(valueId)
                .orElseThrow(() -> new EntityNotFoundException("Valor de parámetro no encontrado con ID: " + valueId));
        value.setActive(false);
        valueRepository.save(value);
    }

    private ParameterTopic requireTopic(String topicCode) {
        if (topicCode == null || topicCode.isBlank()) {
            throw new IllegalArgumentException("El código del tema es obligatorio");
        }
        return topicRepository.findByCode(topicCode.trim().toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException("Tema de parámetros no encontrado con código: " + topicCode));
    }

    private static String normalizeCode(String rawCode, String requiredMessage) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException(requiredMessage);
        }
        return rawCode.trim().toUpperCase();
    }
}
