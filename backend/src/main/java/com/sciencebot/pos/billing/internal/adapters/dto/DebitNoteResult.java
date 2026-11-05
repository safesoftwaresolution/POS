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
    String errorMessage,
    String providerDocumentId
) {

    /** Forma sin el id del documento en el proveedor (la del proveedor y el mock). */
    public DebitNoteResult(boolean success, String debitNoteNumber, String cude, String qrCode, String pdfUrl,
                           String status, LocalDateTime validatedAt, String errorMessage) {
        this(success, debitNoteNumber, cude, qrCode, pdfUrl, status, validatedAt, errorMessage, null);
    }

    public DebitNoteResult withProviderDocumentId(String providerDocumentId) {
        return new DebitNoteResult(success, debitNoteNumber, cude, qrCode, pdfUrl, status, validatedAt, errorMessage,
                providerDocumentId);
    }

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
