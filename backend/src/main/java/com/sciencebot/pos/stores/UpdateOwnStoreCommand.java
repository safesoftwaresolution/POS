package com.sciencebot.pos.stores;

/**
 * Datos que el propio ADMINISTRATOR puede editar de su local, sin pasar por el backoffice.
 * A diferencia de {@link UpdateStoreCommand}, deliberadamente NO incluye email/storeCategoryId:
 * el email esta atado al flujo de verificacion y el estado del local es potestad del backoffice.
 */
public record UpdateOwnStoreCommand(
    String name,
    String phone,
    String website,
    String address,
    String taxId
) {}
