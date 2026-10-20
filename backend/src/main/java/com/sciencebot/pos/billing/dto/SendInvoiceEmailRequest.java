package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendInvoiceEmailRequest(
        @Schema(description = "Correo electrónico destino donde se enviará el ZIP con PDF y XML de la factura", example = "cliente@correo.com")
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "Formato de correo electrónico inválido")
        String email
) {}

