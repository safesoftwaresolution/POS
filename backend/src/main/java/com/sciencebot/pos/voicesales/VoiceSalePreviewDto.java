package com.sciencebot.pos.voicesales;

import java.math.BigDecimal;
import java.util.List;

/** Editable suggestions only; no line in this response represents a registered sale. */
public record VoiceSalePreviewDto(List<Line> items, Reference customer, Reference paymentMethod,
                                  BigDecimal cashReceived, List<String> pendingFields) {
    public record Line(Reference item, Integer quantity) {}
    public enum Status { RESOLVED, AMBIGUOUS, NOT_FOUND, NEEDS_REVIEW, NOT_PROVIDED }
    public enum Kind { PRODUCT, SERVICE, CUSTOMER, PAYMENT_METHOD }
    public record Reference(String mention, Status status, List<Candidate> candidates) {}
    public record Candidate(Kind kind, Long id, String code, String label) {}
}
