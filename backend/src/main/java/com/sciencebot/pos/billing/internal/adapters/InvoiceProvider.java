package com.sciencebot.pos.billing.internal.adapters;

import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;

/**
 * Puerto de salida (SPI) para operaciones de emisión y consulta de Facturas Electrónicas ante el proveedor DIAN.
 */
public interface InvoiceProvider {

    /**
     * Identificador del proveedor (ej. "factus", "mock").
     */
    String getProviderName();

    /**
     * Emite y valida una factura electrónica ante el proveedor y la DIAN.
     */
    InvoiceResult emitInvoice(InvoiceRequest request);

    /**
     * Consulta el estado de una factura electrónica por su número legal o referencia.
     */
    InvoiceResult queryInvoice(String legalNumber);

    /**
     * Descarga el PDF de la factura electrónica.
     */
    default byte[] downloadInvoicePDF(String legalNumber) {
        throw new UnsupportedOperationException("downloadInvoicePDF no soportado por " + getProviderName());
    }

    /**
     * Descarga el XML legal de la factura electrónica.
     */
    default byte[] downloadInvoiceXML(String legalNumber) {
        throw new UnsupportedOperationException("downloadInvoiceXML no soportado por " + getProviderName());
    }

    /**
     * Elimina una factura que no ha sido validada ante la DIAN.
     */
    default boolean deleteUnvalidatedInvoice(String legalNumber) {
        throw new UnsupportedOperationException("deleteUnvalidatedInvoice no soportado por " + getProviderName());
    }

    /**
     * Envía la factura electrónica por correo electrónico (ZIP con PDF y XML).
     */
    default boolean sendInvoiceEmail(String legalNumber, String email) {
        throw new UnsupportedOperationException("sendInvoiceEmail no soportado por " + getProviderName());
    }
}
