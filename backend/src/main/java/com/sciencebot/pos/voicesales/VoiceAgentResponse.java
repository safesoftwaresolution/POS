package com.sciencebot.pos.voicesales;

import java.util.List;

/** The agent's reply and the ticket it leaves. Nothing here is a registered sale. */
public record VoiceAgentResponse(String reply, VoiceTicketDto ticket, List<String> warnings) {}
