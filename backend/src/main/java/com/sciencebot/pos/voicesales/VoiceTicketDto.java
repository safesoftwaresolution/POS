package com.sciencebot.pos.voicesales;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ticket as the voice agent leaves it. Prices and totals come from the store catalog, never from the model.
 * Nothing here is a registered sale: the cashier confirms in the POS, which calls the existing sales API.
 */
public record VoiceTicketDto(List<Line> lines, Customer customer, PaymentMethod paymentMethod, BigDecimal cashReceived,
                             BigDecimal subtotal, boolean confirmationRequested) {
    /** {@code kind} is PRODUCT or SERVICE, the same values as {@link VoiceSalePreviewDto.Kind}. */
    public record Line(String kind, Long id, String code, String name, BigDecimal unitPrice, int quantity) {}
    public record Customer(Long id, String name) {}
    public record PaymentMethod(String code, String label) {}
}
