package com.sciencebot.pos.billing.internal.adapters.dto;

import java.time.LocalDateTime;

public record DebitNoteResult(

    boolean success,
    String debitNoteNumber,
    String cude,
    String qrCode,
    String pdfUrl,
    String status, // VALIDATED, REJECTED, PENDING, ERROR
    LocalDateTime validatedAt,
    String errorMessage

) { 
    public boolean isSuccess() {
    return success;
    }   

    public static DebitNoteResult success(String debitNoteNumber, String cude, String qrCode, String pdfUrl) {
        return new DebitNoteResult(true, debitNoteNumber, cude, qrCode, pdfUrl, "VALIDATED", LocalDateTime.now(), null);
    }

    public static DebitNoteResult pending(String errorMessage) {
        return new DebitNoteResult(false, null, null, null, null, "PENDING", null, errorMessage);
    }

    public static DebitNoteResult rejected(String errorMessage) {
        return new DebitNoteResult(false, null, null, null, null, "REJECTED", null, errorMessage);
    }

    public static DebitNoteResult error(String errorMessage) {
        return new DebitNoteResult(false, null, null, null, null, "ERROR", null, errorMessage);
    }

}
