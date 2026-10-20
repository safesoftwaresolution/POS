package com.sciencebot.pos.support;

public record SupportMetricsDto(
    long totalOpen, long inProgress, long resolved, long closed,
    long bugsCritical, long totalTickets
) {}