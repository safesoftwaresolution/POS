package com.sciencebot.pos.audit.internal.mappers;

import com.sciencebot.pos.audit.AuditEntryDto;
import com.sciencebot.pos.audit.FieldChange;
import com.sciencebot.pos.audit.internal.entities.AuditEntry;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Component
public class AuditEntryMapper {

    private static final TypeReference<Map<String, FieldChange>> CHANGES = new TypeReference<>() { };

    private final JsonMapper json = JsonMapper.builder().build();

    public String writeChanges(Map<String, FieldChange> changes) {
        return (changes == null || changes.isEmpty()) ? null : json.writeValueAsString(changes);
    }

    public AuditEntryDto toDto(AuditEntry entry) {
        Map<String, FieldChange> changes = (entry.getChanges() == null || entry.getChanges().isBlank())
                ? Map.of()
                : json.readValue(entry.getChanges(), CHANGES);
        return new AuditEntryDto(entry.getId(), entry.getEntityType(), entry.getEntityId(), entry.getAction(),
                entry.getUserId(), entry.getUsername(), changes, entry.getCreatedAt());
    }
}
