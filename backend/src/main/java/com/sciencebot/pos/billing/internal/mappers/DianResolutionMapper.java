package com.sciencebot.pos.billing.internal.mappers;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.internal.adapters.dto.DianResolutionData;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Resoluciones de facturación que la DIAN tiene para un NIT: cómo se muestran (como rango aún sin registrar) y cómo
 * se registran como rango de facturas del local (documento {@code 01}, empezando en el primer número).
 */
@Component
public class DianResolutionMapper {

    public static final String INVOICE_DOCUMENT = "01";
    private static final String INVOICE_DOCUMENT_NAME = "Factura de Venta";

    /** La resolución tal como la tiene la DIAN: sin id (no es un rango registrado) ni estado activo. */
    public NumberingRangeDto toDto(DianResolutionData resolution, LocalDate today) {
        boolean expired = resolution.endDate() != null && today.isAfter(resolution.endDate());
        return new NumberingRangeDto(null, INVOICE_DOCUMENT, resolution.prefix(), resolution.resolutionNumber(),
                resolution.minNumber(), resolution.maxNumber(), resolution.minNumber(),
                toText(resolution.startDate()), toText(resolution.endDate()), resolution.technicalKey(),
                null, INVOICE_DOCUMENT_NAME, expired);
    }

    /** Solicitud para registrar la resolución como rango de facturas del local, desde su primer número. */
    public CreateNumberingRangeRequest toInvoiceRange(DianResolutionData resolution) {
        return new CreateNumberingRangeRequest(INVOICE_DOCUMENT, resolution.prefix(), resolution.resolutionNumber(),
                resolution.minNumber(), resolution.minNumber(), resolution.maxNumber(),
                toText(resolution.startDate()), toText(resolution.endDate()), resolution.technicalKey());
    }

    private static String toText(LocalDate date) {
        return date != null ? date.toString() : null;
    }
}
