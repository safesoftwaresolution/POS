package com.sciencebot.pos.auth.internal.listeners;

import com.sciencebot.pos.auth.internal.services.AuthService;
import com.sciencebot.pos.users.PasswordResetByAdminEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Sincrono: corre en la misma transaccion del cambio de contrasena, asi que ambos efectos se confirman o se revierten juntos. */
@Component
class PasswordResetByAdminListener {

    private final AuthService authService;

    PasswordResetByAdminListener(AuthService authService) {
        this.authService = authService;
    }

    @EventListener
    void onPasswordResetByAdmin(PasswordResetByAdminEvent event) {
        authService.closeSessionsAndClearLockout(event.userId(), event.username());
    }
}
