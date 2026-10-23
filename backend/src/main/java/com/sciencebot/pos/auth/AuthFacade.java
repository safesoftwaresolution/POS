package com.sciencebot.pos.auth;

public interface AuthFacade {

    /**
     * Reenvía el enlace de verificación de correo a un usuario concreto (acción del SUPER_ADMIN desde el
     * backoffice). A diferencia del reenvío público, informa de los problemas: cuenta ya verificada,
     * reenvío demasiado reciente o fallo del proveedor de correo.
     */
    void resendVerificationToUser(Long userId);
}
