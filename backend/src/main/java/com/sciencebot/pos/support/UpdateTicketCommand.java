package com.sciencebot.pos.support;

public record UpdateTicketCommand(String status, String priority, String resolutionNotes) {}