package com.sciencebot.pos.billing.internal.adapters.factus;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador de Facturación Electrónica para Factus (API REST DIAN v2).
 * Compone y delega a {@link FactusInvoiceClient}, {@link FactusNumberingRangeClient},
 * {@link FactusCreditNoteClient} y {@link FactusDebitNoteClient}.
 */
@Component("factusBillingProvider")
public class FactusBillingAdapter implements ElectronicInvoicingProvider {

    private final FactusInvoiceClient invoiceClient;
    private final FactusNumberingRangeClient rangeClient;
    private final FactusCreditNoteClient creditNoteClient;
    private final FactusDebitNoteClient debitNoteClient;

    public FactusBillingAdapter(
            FactusInvoiceClient invoiceClient,
            FactusNumberingRangeClient rangeClient,
            FactusCreditNoteClient creditNoteClient,
            FactusDebitNoteClient debitNoteClient
    ) {
        this.invoiceClient = invoiceClient;
        this.rangeClient = rangeClient;
        this.creditNoteClient = creditNoteClient;
        this.debitNoteClient = debitNoteClient;
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

    // --- Notas Crédito (delegado a FactusCreditNoteClient) ---

    @Override
    public CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId) {
        return creditNoteClient.emitCreditNote(request, storeId);
    }

    @Override
    public byte[] downloadCreditNotePDF(String creditNoteNumber, Long storeId) {
        return creditNoteClient.downloadCreditNotePDF(creditNoteNumber, storeId);
    }

    @Override
    public byte[] downloadCreditNoteXML(String creditNoteNumber, Long storeId) {
        return creditNoteClient.downloadCreditNoteXML(creditNoteNumber, storeId);
    }

    @Override
    public boolean deleteUnvalidatedCreditNote(String referenceCode, Long storeId) {
        return creditNoteClient.deleteUnvalidatedCreditNote(referenceCode, storeId);
    }

    // --- Notas Débito (delegado a FactusDebitNoteClient) ---

    @Override
    public DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId) {
        return debitNoteClient.emitDebitNote(request, storeId);
    }

    @Override
    public byte[] downloadDebitNotePDF(String debitNoteNumber, Long storeId) {
        return debitNoteClient.downloadDebitNotePDF(debitNoteNumber, storeId);
    }

    @Override
    public byte[] downloadDebitNoteXML(String debitNoteNumber, Long storeId) {
        return debitNoteClient.downloadDebitNoteXML(debitNoteNumber, storeId);
    }

    @Override
    public boolean deleteUnvalidatedDebitNote(String referenceCode, Long storeId) {
        return debitNoteClient.deleteUnvalidatedDebitNote(referenceCode, storeId);
    }
}
