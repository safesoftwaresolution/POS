package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciales de autenticación con el proveedor de facturación para un entorno de un local. "
        + "Un secret o password vacío conserva el valor guardado. "
        + "El rango de numeración lo elige el administrador del local "
        + "(POST /api/v1/billing/numbering-ranges/{id}/set-default).")
public record UpdateBillingCredentialsCommand(
        String clientId,
        String clientSecret,
        String username,
        String password
) {}
