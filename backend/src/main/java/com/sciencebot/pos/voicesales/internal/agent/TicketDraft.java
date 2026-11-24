package com.sciencebot.pos.voicesales.internal.agent;

import com.sciencebot.pos.voicesales.VoiceTicketDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable ticket state for one agent turn. Any change withdraws a pending confirmation: the cashier must
 * confirm exactly what is on screen, never an earlier version.
 */
final class TicketDraft {
    private final Map<String, VoiceTicketDto.Line> lines = new LinkedHashMap<>();
    private VoiceTicketDto.Customer customer;
    private VoiceTicketDto.PaymentMethod paymentMethod;
    private BigDecimal cashReceived;
    private boolean confirmationRequested;

    static String key(String kind, Long id) {
        return kind + ":" + id;
    }

    List<VoiceTicketDto.Line> lines() {
        return new ArrayList<>(lines.values());
    }

    VoiceTicketDto.Line line(String kind, Long id) {
        return lines.get(key(kind, id));
    }

    void putLine(VoiceTicketDto.Line line) {
        lines.put(key(line.kind(), line.id()), line);
        confirmationRequested = false;
    }

    void removeLine(VoiceTicketDto.Line line) {
        lines.remove(key(line.kind(), line.id()));
        confirmationRequested = false;
    }

    VoiceTicketDto.Customer customer() {
        return customer;
    }

    void customer(VoiceTicketDto.Customer customer) {
        this.customer = customer;
        confirmationRequested = false;
    }

    VoiceTicketDto.PaymentMethod paymentMethod() {
        return paymentMethod;
    }

    void paymentMethod(VoiceTicketDto.PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
        confirmationRequested = false;
    }

    BigDecimal cashReceived() {
        return cashReceived;
    }

    void cashReceived(BigDecimal cashReceived) {
        this.cashReceived = cashReceived;
        confirmationRequested = false;
    }

    boolean confirmationRequested() {
        return confirmationRequested;
    }

    void requestConfirmation() {
        confirmationRequested = true;
    }

    void clear() {
        lines.clear();
        customer = null;
        paymentMethod = null;
        cashReceived = null;
        confirmationRequested = false;
    }
}
