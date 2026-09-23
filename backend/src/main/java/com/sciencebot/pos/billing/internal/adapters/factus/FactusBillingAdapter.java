package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador de Facturación Electrónica para Factus (API REST DIAN v2).
 * Compone y delega a {@link FactusInvoiceClient} y
 * {@link FactusNumberingRangeClient}.
 */
@Component("factusBillingProvider")
public class FactusBillingAdapter implements ElectronicInvoicingProvider {

    private final FactusInvoiceClient invoiceClient;
    private final FactusNumberingRangeClient rangeClient;

    public FactusBillingAdapter(FactusInvoiceClient invoiceClient, FactusNumberingRangeClient rangeClient) {
        this.invoiceClient = invoiceClient;
        this.rangeClient = rangeClient;
    }

    @Override
    public String getProviderName() {
        return "factus";
    }

    // --- Facturas (delegado a FactusInvoiceClient) ---

    @Override
    public InvoiceResult emitInvoice(InvoiceRequest request) {
        return invoiceClient.emitInvoice(request);
    }

    @Override
    public InvoiceResult queryInvoice(String legalNumber) {
        return invoiceClient.queryInvoice(legalNumber);
    }

    @Override
    public byte[] downloadInvoicePDF(String legalNumber) {
        return invoiceClient.downloadInvoicePDF(legalNumber);
    }

    @Override
    public byte[] downloadInvoiceXML(String legalNumber) {
        return invoiceClient.downloadInvoiceXML(legalNumber);
    }

    @Override
    public boolean deleteUnvalidatedInvoice(String legalNumber) {
        return invoiceClient.deleteUnvalidatedInvoice(legalNumber);
    }

    @Override
    public boolean sendInvoiceEmail(String legalNumber, String email) {
        return invoiceClient.sendInvoiceEmail(legalNumber, email);
    }

    // --- Rangos de Numeración (delegado a FactusNumberingRangeClient) ---

    @Override
    public List<NumberingRangeDto> queryDianNumberingRanges() {
        return rangeClient.queryDianNumberingRanges();
    }

    @Override
    public List<NumberingRangeDto> listNumberingRanges() {
        return rangeClient.listNumberingRanges();
    }

    @Override
    public NumberingRangeDto getNumberingRange(Long numberingRangeId) {
        return rangeClient.getNumberingRange(numberingRangeId);
    }

    @Override
    public NumberingRangeDto createNumberingRange(CreateNumberingRangeRequest request) {
        return rangeClient.createNumberingRange(request);
    }

    @Override
    public void deleteNumberingRange(Long numberingRangeId) {
        rangeClient.deleteNumberingRange(numberingRangeId);
    }

    @Override
    public NumberingRangeDto toggleNumberingRangeStatus(Long numberingRangeId) {
        return rangeClient.toggleNumberingRangeStatus(numberingRangeId);
    }

    @Override
    public NumberingRangeDto updateNumberingRangeCurrent(Long numberingRangeId, Long current) {
        return rangeClient.updateNumberingRangeCurrent(numberingRangeId, current);
    }
}
