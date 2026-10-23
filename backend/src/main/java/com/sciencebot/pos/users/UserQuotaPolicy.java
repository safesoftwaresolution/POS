package com.sciencebot.pos.users;

import java.util.Optional;

/**
 * Puerto que el módulo {@code users} define y que otro módulo (hoy {@code stores}, dueño de los planes)
 * implementa. Así {@code users} aplica el tope sin depender de {@code stores} y se evita un ciclo entre módulos.
 */
public interface UserQuotaPolicy {

    /** Tope de usuarios activos del local según su plan; vacío si el local no tiene un tope aplicable. */
    Optional<UserQuota> quotaFor(Long storeId);
}
