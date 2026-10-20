package com.sciencebot.pos.support;

public record CreateTicketCommand(
    String type, String priority, String title, String description,
    String contactName, String contactEmail, String contactPhone,
    Long storeId, String systemInfo
) {}