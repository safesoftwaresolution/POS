package com.sciencebot.pos.billing;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Credenciales que el proveedor entrega para un entorno de un local. "
        + "Un secret o password vacío conserva el valor guardado.")
public record UpdateBillingCredentialsCommand(
        String clientId,
        String clientSecret,
        String username,
        String password,
        Integer numberingRangeId
) {}
