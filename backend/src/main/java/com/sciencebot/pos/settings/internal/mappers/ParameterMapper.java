package com.sciencebot.pos.settings.internal.mappers;

import com.sciencebot.pos.settings.ParameterTopicDto;
import com.sciencebot.pos.settings.ParameterValueDto;
import com.sciencebot.pos.settings.internal.entities.ParameterTopic;
import com.sciencebot.pos.settings.internal.entities.ParameterValue;
import org.springframework.stereotype.Component;

@Component
public class ParameterMapper {

    public ParameterTopicDto toTopicDto(ParameterTopic topic) {
        if (topic == null) {
            return null;
        }
        return new ParameterTopicDto(
                topic.getId(),
                topic.getCode(),
                topic.getName(),
                topic.getDescription(),
                topic.isSystem()
        );
    }

    public ParameterValueDto toValueDto(ParameterValue value) {
        if (value == null) {
            return null;
        }
        return new ParameterValueDto(
                value.getId(),
                value.getTopic().getCode(),
                value.getCode(),
                value.getLabel(),
                value.getExtraValue(),
                value.getSortOrder(),
                value.isActive()
        );
    }
}
