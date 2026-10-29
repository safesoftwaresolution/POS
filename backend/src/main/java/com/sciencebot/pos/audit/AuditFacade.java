package com.sciencebot.pos.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/** API pública de la bitácora de auditoría. Todo se registra y se consulta dentro del local actual. */
public interface AuditFacade {

    /**
     * Registra un cambio sobre una entidad del local actual. Se ejecuta en la transacción de quien llama: si el
     * cambio se revierte, el registro también.
     *
     * @param changes campos modificados; puede ser vacío (por ejemplo al dar de baja)
     */
    void record(String entityType, Long entityId, AuditAction action, Long userId, String username,
                Map<String, FieldChange> changes);

    /** Historial de una entidad del local actual, del cambio más reciente al más antiguo. */
    Page<AuditEntryDto> history(String entityType, Long entityId, Pageable pageable);
}
