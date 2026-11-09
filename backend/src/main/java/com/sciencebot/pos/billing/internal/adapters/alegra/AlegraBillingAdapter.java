package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptador de Alegra (proveedor tecnológico DIAN). Cubre la cuenta (configuración, prueba de conexión y caché
 * de la empresa), los rangos de numeración locales y la emisión de facturas; las notas crédito y débito llegan en
 * FEAT-026 y mientras tanto devuelven un resultado de error claro en lugar de fingir una emisión.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraBillingAdapter implements ElectronicInvoicingProvider {

    private static final String PENDING = "Alegra: %s no está implementada todavía";

    private final AlegraAccessResolver accessResolver;
    private final AlegraCompanyProvider companyProvider;
    private final AlegraNumberingRangeClient rangeClient;
    private final AlegraInvoiceClient invoiceClient;
    private final AlegraNoteClient noteClient;

    public AlegraBillingAdapter(AlegraAccessResolver accessResolver,
                                AlegraCompanyProvider companyProvider,
                                AlegraNumberingRangeClient rangeClient,
                                AlegraInvoiceClient invoiceClient,
                                AlegraNoteClient noteClient) {
        this.accessResolver = accessResolver;
        this.companyProvider = companyProvider;
        this.rangeClient = rangeClient;
        this.invoiceClient = invoiceClient;
        this.noteClient = noteClient;
    }

    @Override
    public String getProviderName() {
        return "alegra";
    }

    // --- Cuenta ---

    @Override
    public boolean isAccountConfigured(Long storeId, BillingEnvironment environment) {
        return accessResolver.isConfigured(storeId, environment);
    }

    /** Lee la empresa del local en Alegra con su token y company.id; un fallo sale como excepción con el mensaje. */
    @Override
    public void verifyConnection(Long storeId, BillingEnvironment environment) {
        AlegraAccess access = accessResolver.resolveIgnoringStatus(storeId, environment);
        companyProvider.fetch(access);
    }

    @Override
    public void onAccountChanged(Long storeId) {
        companyProvider.invalidate(storeId);
    }

    @Override
    public String displayName() {
        return "Alegra";
    }

    // --- Facturas ---

    @Override
    public InvoiceResult emitInvoice(InvoiceRequest request) {
        return invoiceClient.emitInvoice(request);
    }

    @Override
    public InvoiceResult emitInvoice(InvoiceRequest request, BillingDocumentContext context) {
        return invoiceClient.emitInvoice(request, context);
    }

    @Override
    public InvoiceResult queryInvoiceByProviderId(String id, BillingDocumentContext context) {
        return invoiceClient.queryInvoiceByProviderId(id, context);
    }

    @Override
    public boolean sendInvoiceEmailByProviderId(String id, String email, BillingDocumentContext context) {
        return invoiceClient.sendInvoiceEmail(id, email, context);
    }

    @Override
    public byte[] downloadInvoicePDFByProviderId(String id, BillingDocumentContext context) {
        return invoiceClient.downloadInvoicePDF(id, context);
    }

    @Override
    public byte[] downloadInvoiceXMLByProviderId(String id, BillingDocumentContext context) {
        return invoiceClient.downloadInvoiceXML(id, context);
    }

    @Override
    public InvoiceResult queryInvoiceByProviderId(String providerDocumentId) {
        return invoiceClient.queryInvoiceByProviderId(providerDocumentId);
    }

    @Override
    public boolean sendInvoiceEmailByProviderId(String providerDocumentId, String email) {
        return invoiceClient.sendInvoiceEmail(providerDocumentId, email);
    }

    @Override
    public byte[] downloadInvoiceXMLByProviderId(String providerDocumentId) {
        return invoiceClient.downloadInvoiceXML(providerDocumentId);
    }

    @Override
    public byte[] downloadInvoicePDFByProviderId(String providerDocumentId) {
        return invoiceClient.downloadInvoicePDF(providerDocumentId);
    }

    /** Alegra consulta por el id del documento ({@link #queryInvoiceByProviderId}), no por el número legal. */
    @Override
    public InvoiceResult queryInvoice(String legalNumber) {
        return InvoiceResult.error(String.format(PENDING, "la consulta de facturas por número legal"));
    }

    // --- Notas crédito y débito ---

    @Override
    public CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId) {
        return noteClient.emitCreditNote(request, storeId);
    }

    @Override
    public DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId) {
        return noteClient.emitDebitNote(request, storeId);
    }

    // --- Rangos de numeración (tablas locales) ---

    @Override
    public List<NumberingRangeDto> queryDianNumberingRanges() {
        return rangeClient.queryDianNumberingRanges();
    }

    @Override
    public boolean hasUsableInvoiceRange(Long storeId, BillingEnvironment environment) {
        return rangeClient.hasUsableInvoiceRange(storeId, environment);
    }

    @Override
    public int countInvoiceRanges(Long storeId, BillingEnvironment environment) {
        return rangeClient.countInvoiceRanges(storeId, environment);
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
    @Override
    public CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId,
            BillingDocumentContext context, List<InvoiceItemData> canonicalItems) {
        return noteClient.emitCreditNote(request, storeId, context, canonicalItems);
    }

    @Override
    public CreditNoteResult retryCreditNote(String reference, BillingDocumentContext context) {
        return noteClient.retryCreditNote(reference, context);
    }

    @Override
    public CreditNoteResult queryCreditNote(String id, BillingDocumentContext context) {
        return noteClient.queryCreditNote(id, context);
    }

    @Override
    public byte[] downloadCreditNotePDF(String number, String id, Long storeId, BillingDocumentContext context) {
        return noteClient.download("/credit-notes", id, context, true);
    }

    @Override
    public byte[] downloadCreditNoteXML(String number, String id, Long storeId, BillingDocumentContext context) {
        return noteClient.download("/credit-notes", id, context, false);
    }    @Override
    public DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId,
            BillingDocumentContext context, List<InvoiceItemData> canonicalItems) {
        return noteClient.emitDebitNote(request, storeId, context, canonicalItems);
    }

    @Override
    public DebitNoteResult retryDebitNote(String reference, BillingDocumentContext context) {
        return noteClient.retryDebitNote(reference, context);
    }

    @Override
    public DebitNoteResult queryDebitNote(String id, BillingDocumentContext context) {
        return noteClient.queryDebitNote(id, context);
    }

    @Override
    public byte[] downloadDebitNotePDF(String number, String id, Long storeId, BillingDocumentContext context) {
        return noteClient.download("/debit-notes", id, context, true);
    }

    @Override
    public byte[] downloadDebitNoteXML(String number, String id, Long storeId, BillingDocumentContext context) {
        return noteClient.download("/debit-notes", id, context, false);
    }
}
