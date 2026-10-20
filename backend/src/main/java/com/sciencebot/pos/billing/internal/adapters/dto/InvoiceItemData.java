package com.sciencebot.pos.billing.internal.adapters.dto;

import java.math.BigDecimal;

public record InvoiceItemData(
        String productCode,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal subtotal,
        String unitMeasureCode,
        String standardCode,
        String taxCode,
        BigDecimal discountRate,
        boolean isExcluded
) {
    // Constructor de conveniencia para compatibilidad con código existente
    public InvoiceItemData(
            String productCode,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            BigDecimal subtotal
    ) {
        this(
                productCode,
                productName,
                BigDecimal.valueOf(quantity),
                unitPrice,
                taxRate != null ? taxRate : BigDecimal.ZERO,
                subtotal,
                "94",
                "999",
                "01",
                BigDecimal.ZERO,
                false
        );
    }
}
