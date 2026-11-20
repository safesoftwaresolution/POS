package com.sciencebot.pos.businesses;

import jakarta.validation.constraints.NotNull;

public record AddBranchCommand(@NotNull(message = "El local es obligatorio.") Long storeId) {}
