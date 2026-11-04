package com.sciencebot.pos.billing.internal.adapters.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param paymentForm    1 = Contado, 2 = Crédito
 * @param paymentDueDate fecha de vencimiento; solo se envía cuando {@code paymentForm} es "2" (venta a fiado)
 */
public record InvoiceRequest(
        Long saleId,
        Long storeId,
        Integer numberingRangeId,
        String invoiceNumber,
        String paymentForm,
        String paymentMethodCode,
        CustomerBillingData customer,
        List<InvoiceItemData> items,
        BigDecimal totalAmount,
        String observation,
        LocalDate paymentDueDate
) {
    public InvoiceRequest(
            Long saleId,
            Long storeId,
            Integer numberingRangeId,
            String invoiceNumber,
            String paymentForm,
            String paymentMethodCode,
            CustomerBillingData customer,
            List<InvoiceItemData> items,
            BigDecimal totalAmount,
            String observation
    ) {
        this(saleId, storeId, numberingRangeId, invoiceNumber, paymentForm, paymentMethodCode, customer, items,
                totalAmount, observation, null);
    }

    public InvoiceRequest(
            Long saleId,
            String invoiceNumber,
            String paymentForm,
            String paymentMethodCode,
            CustomerBillingData customer,
            List<InvoiceItemData> items,
            BigDecimal totalAmount,
            String observation
    ) {
        this(saleId, null, null, invoiceNumber, paymentForm, paymentMethodCode, customer, items, totalAmount, observation);
    }

    // Constructor de conveniencia para compatibilidad con llamadas existentes
    public InvoiceRequest(
            Long saleId,
            String invoiceNumber,
            String paymentMethodCode,
            CustomerBillingData customer,
            List<InvoiceItemData> items,
            BigDecimal totalAmount
    ) {
        this(saleId, invoiceNumber, "1", paymentMethodCode, customer, items, totalAmount, null);
    }
}
