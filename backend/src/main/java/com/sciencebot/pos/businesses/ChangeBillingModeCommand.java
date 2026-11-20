package com.sciencebot.pos.businesses;

import jakarta.validation.constraints.NotNull;

public record ChangeBillingModeCommand(@NotNull(message = "El modo de facturación es obligatorio.") BillingMode billingMode) {}
