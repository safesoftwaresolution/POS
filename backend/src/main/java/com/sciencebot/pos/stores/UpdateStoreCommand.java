package com.sciencebot.pos.stores;

public record UpdateStoreCommand(
    String name,
    Long storeCategoryId,
    String phone,
    String email,
    String website,
    String address,
    String taxId
) {}