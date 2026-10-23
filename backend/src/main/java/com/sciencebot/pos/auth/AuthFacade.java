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

    /**
     * Inactiva o reactiva a un usuario (acción del SUPER_ADMIN). Al inactivar revoca sus sesiones. Falla con un
     * SUPER_ADMIN, con uno mismo, con el último administrador activo del local o si reactivar supera el plan.
     */
    void setAccountActive(Long userId, boolean active);

    /**
     * Elimina definitivamente a un usuario ya verificado y sus tokens (acción del SUPER_ADMIN). Falla (409) si
     * tiene historial de ventas, compras, movimientos o devoluciones: en ese caso solo se puede inactivar.
     */
    void deleteAccount(Long userId);
}
