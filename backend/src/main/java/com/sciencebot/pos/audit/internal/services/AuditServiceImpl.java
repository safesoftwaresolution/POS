package com.sciencebot.pos.audit.internal.services;

import com.sciencebot.pos.audit.AuditAction;
import com.sciencebot.pos.audit.AuditEntryDto;
import com.sciencebot.pos.audit.AuditFacade;
import com.sciencebot.pos.audit.FieldChange;
import com.sciencebot.pos.audit.internal.entities.AuditEntry;
import com.sciencebot.pos.audit.internal.mappers.AuditEntryMapper;
import com.sciencebot.pos.audit.internal.repositories.AuditEntryRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class AuditServiceImpl implements AuditFacade {

    private final AuditEntryRepository repository;
    private final AuditEntryMapper mapper;

    public AuditServiceImpl(AuditEntryRepository repository, AuditEntryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void record(String entityType, Long entityId, AuditAction action, Long userId, String username,
                       Map<String, FieldChange> changes) {
        Objects.requireNonNull(entityType, "El tipo de entidad es obligatorio");
        Objects.requireNonNull(entityId, "El id de la entidad es obligatorio");
        Objects.requireNonNull(action, "La acción es obligatoria");

        AuditEntry entry = new AuditEntry();
        entry.setStoreId(requireStoreId());
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setUserId(userId);
        entry.setUsername(username);
        entry.setChanges(mapper.writeChanges(changes));
        repository.save(entry);
    }

    @Override
    public Page<AuditEntryDto> history(String entityType, Long entityId, Pageable pageable) {
        return repository
                .findByStoreIdAndEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(requireStoreId(), entityType, entityId, pageable)
                .map(mapper::toDto);
    }

    private static Long requireStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
