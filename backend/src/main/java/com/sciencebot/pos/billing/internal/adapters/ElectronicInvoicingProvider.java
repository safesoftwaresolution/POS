package com.sciencebot.pos.billing.internal.adapters;

/**
 * Interfaz Strategy unificada para proveedores de Facturación Electrónica DIAN.
 * Compone los puertos especializados {@link InvoiceProvider} y {@link NumberingRangeProvider}.
 */
public interface ElectronicInvoicingProvider extends InvoiceProvider, NumberingRangeProvider {

    /**
     * Identificador único de la estrategia (ej. "factus", "mock", "siigo").
     */
    @Override
    String getProviderName();
}
