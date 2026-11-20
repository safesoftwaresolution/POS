package com.sciencebot.pos.auth;

/** Branch to work on. {@code refreshToken} is optional: the HttpOnly cookie is used when it is absent. */
public record SwitchStoreRequest(Long storeId, String refreshToken) {}
