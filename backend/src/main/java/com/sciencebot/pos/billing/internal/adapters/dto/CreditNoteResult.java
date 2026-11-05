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
        LocalDateTime validatedAt,
        String providerDocumentId
) {

    /** Forma sin el id del documento en el proveedor (la del proveedor y el mock). */
    public CreditNoteResult(boolean isSuccess, String creditNoteNumber, String cude, String qrCode, String pdfUrl,
                            String status, String errorMessage, LocalDateTime validatedAt) {
        this(isSuccess, creditNoteNumber, cude, qrCode, pdfUrl, status, errorMessage, validatedAt, null);
    }

    public CreditNoteResult withProviderDocumentId(String providerDocumentId) {
        return new CreditNoteResult(isSuccess, creditNoteNumber, cude, qrCode, pdfUrl, status, errorMessage,
                validatedAt, providerDocumentId);
    }

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
