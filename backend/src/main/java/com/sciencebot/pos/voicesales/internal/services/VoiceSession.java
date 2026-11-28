package com.sciencebot.pos.voicesales.internal.services;

import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/** The authenticated store session every voice operation requires before any provider is called. */
public final class VoiceSession {
    private VoiceSession() {}

    public record Actor(long store, String username) {}

    public static Actor require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Long store = TenantContext.getStoreId();
        if (store == null || authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PosUserDetails user)
                || !user.isEnabled() || !store.equals(user.getStoreId())) {
            throw new AccessDeniedException("Se requiere una sesión de tienda.");
        }
        return new Actor(store, user.getUsername());
    }
}
