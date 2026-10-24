package com.sciencebot.pos.backoffice;

/** Local que no tiene ningún administrador activo asignado. */
public record StoreWithoutOwnerDto(
    Long id,
    String name,
    String email,
    String taxId,
    String status
) {}
