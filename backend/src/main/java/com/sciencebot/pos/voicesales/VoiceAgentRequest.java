package com.sciencebot.pos.voicesales;

import java.math.BigDecimal;
import java.util.List;

/**
 * One turn of the voice/text agent. The POS sends what the cashier said, the ticket as it stands and a short
 * history. The ticket carries only ids and quantities: names and prices are always re-read from the store catalog.
 */
public record VoiceAgentRequest(String text, Ticket ticket, List<Turn> history) {
    public static final int MAX_TEXT = 500;
    public static final int MAX_HISTORY = 6;
    public static final int MAX_LINES = 30;

    /** {@code kind} is PRODUCT or SERVICE; {@code paymentMethodCode} is a code of the store's PAYMENT_METHODS. */
    public record Ticket(List<Line> lines, Long customerId, String paymentMethodCode, BigDecimal cashReceived) {}
    public record Line(String kind, Long id, Integer quantity) {}
    /** {@code role} is "user" or "assistant". */
    public record Turn(String role, String text) {}
}
