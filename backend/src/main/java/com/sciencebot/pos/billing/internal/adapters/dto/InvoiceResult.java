package com.sciencebot.pos.billing.internal.adapters.dto;

import java.time.LocalDateTime;

public record InvoiceResult(
        boolean isSuccess,
        String legalInvoiceNumber,
        String cufe,
        String qrCode,
        String pdfUrl,
        String status, // VALIDATED, REJECTED, PENDING, ERROR
        String errorMessage,
        LocalDateTime validatedAt,
        String providerDocumentId
) {

    /** Estado de un resultado que no debe reintentarse solo: el local no puede facturar ahora. */
    public static final String NOT_ENABLED = "NOT_ENABLED";

    /** Forma sin el id del documento en el proveedor (la del proveedor y el mock). */
    public InvoiceResult(boolean isSuccess, String legalInvoiceNumber, String cufe, String qrCode, String pdfUrl,
                         String status, String errorMessage, LocalDateTime validatedAt) {
        this(isSuccess, legalInvoiceNumber, cufe, qrCode, pdfUrl, status, errorMessage, validatedAt, null);
    }

    /** El mismo resultado con el id que el proveedor le dio al documento (para conciliarlo después). */
    public InvoiceResult withProviderDocumentId(String providerDocumentId) {
        return new InvoiceResult(isSuccess, legalInvoiceNumber, cufe, qrCode, pdfUrl, status, errorMessage,
                validatedAt, providerDocumentId);
    }

    public static InvoiceResult success(String legalInvoiceNumber, String cufe, String qrCode, String pdfUrl) {
        return new InvoiceResult(true, legalInvoiceNumber, cufe, qrCode, pdfUrl, "VALIDATED", null, LocalDateTime.now());
    }

    public static InvoiceResult pending(String errorMessage) {
        return new InvoiceResult(false, null, null, null, null, "PENDING", errorMessage, null);
    }

    public static InvoiceResult rejected(String errorMessage) {
        return new InvoiceResult(false, null, null, null, null, "REJECTED", errorMessage, null);
    }

    public static InvoiceResult error(String errorMessage) {
        return new InvoiceResult(false, null, null, null, null, "ERROR", errorMessage, null);
    }

    /**
     * El local no puede facturar ahora (facturación no activa, sin empresa o sin rango usable): queda en error y no se
     * reintenta sola, porque reintentar no lo arregla y emitirla después saldría con fecha atrasada.
     */
    public static InvoiceResult notEnabled(String errorMessage) {
        return new InvoiceResult(false, null, null, null, null, NOT_ENABLED, errorMessage, null);
    }
}
