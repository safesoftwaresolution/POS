package com.sciencebot.pos.billing.internal.adapters;

/**
 * Interfaz Strategy unificada para proveedores de Facturación Electrónica DIAN.
 * Compone los puertos especializados {@link InvoiceProvider}, {@link NumberingRangeProvider},
 * {@link CreditNoteProvider}, {@link DebitNoteProvider} y {@link BillingAccountProvider}.
 */
public interface ElectronicInvoicingProvider
        extends InvoiceProvider, NumberingRangeProvider, CreditNoteProvider, DebitNoteProvider, BillingAccountProvider {

    /**
     * Identificador único de la estrategia (ej. "factus", "mock", "siigo").
     */
    @Override
    String getProviderName();
}
