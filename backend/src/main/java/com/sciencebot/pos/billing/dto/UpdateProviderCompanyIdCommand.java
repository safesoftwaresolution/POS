package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "company.id que el proveedor de facturación asignó al local en un entorno")
public record UpdateProviderCompanyIdCommand(
        @NotBlank @Size(max = 64) String companyId
) {}
