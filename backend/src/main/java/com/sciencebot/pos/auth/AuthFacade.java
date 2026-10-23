package com.sciencebot.pos.auth;

public interface AuthFacade {

    /**
     * Reenvía el enlace de verificación de correo a un usuario concreto (acción del SUPER_ADMIN desde el
     * backoffice). A diferencia del reenvío público, informa de los problemas: cuenta ya verificada,
     * reenvío demasiado reciente o fallo del proveedor de correo.
     */
    void resendVerificationToUser(Long userId);

    /**
     * Elimina definitivamente la cuenta de un ADMINISTRATOR que no verificó su correo y no tiene local,
     * junto con sus tokens (acción del SUPER_ADMIN). Falla si la cuenta no cumple esas condiciones.
     */
    void deleteUnverifiedAccount(Long userId);
}
