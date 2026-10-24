package com.sciencebot.pos.users;

/** Tope de usuarios activos de un local según su plan. */
public record UserQuota(String planCode, String planName, int maxActiveUsers) {}
