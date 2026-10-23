package com.sciencebot.pos.billing.internal.adapters.dto;

import java.time.LocalDateTime;

public record CreditNoteResult(
        boolean isSuccess,
        String creditNoteNumber,
        String cude,
        String qrCode,
        String pdfUrl,
        String status, // VALIDATED, REJECTED, PENDING, ERROR
        String errorMessage,
        LocalDateTime validatedAt
) {
    public static CreditNoteResult success(String creditNoteNumber, String cude, String qrCode, String pdfUrl) {
        return new CreditNoteResult(true, creditNoteNumber, cude, qrCode, pdfUrl, "VALIDATED", null, LocalDateTime.now());
    }

    public static CreditNoteResult pending(String errorMessage) {
        return new CreditNoteResult(false, null, null, null, null, "PENDING", errorMessage, null);
    }

    public static CreditNoteResult rejected(String errorMessage) {
        return new CreditNoteResult(false, null, null, null, null, "REJECTED", errorMessage, null);
    }

    public static CreditNoteResult error(String errorMessage) {
        return new CreditNoteResult(false, null, null, null, null, "ERROR", errorMessage, null);
    }
}
