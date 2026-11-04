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

    public AlegraBillingAdapter(AlegraAccessResolver accessResolver,
                                AlegraCompanyProvider companyProvider,
                                AlegraNumberingRangeClient rangeClient,
                                AlegraInvoiceClient invoiceClient) {
        this.accessResolver = accessResolver;
        this.companyProvider = companyProvider;
        this.rangeClient = rangeClient;
        this.invoiceClient = invoiceClient;
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
    public InvoiceResult queryInvoiceByProviderId(String providerDocumentId) {
        return invoiceClient.queryInvoiceByProviderId(providerDocumentId);
    }

    /** Alegra consulta por el id del documento ({@link #queryInvoiceByProviderId}), no por el número legal. */
    @Override
    public InvoiceResult queryInvoice(String legalNumber) {
        return InvoiceResult.error(String.format(PENDING, "la consulta de facturas por número legal"));
    }

    // --- Notas (FEAT-026) ---

    @Override
    public CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId) {
        return CreditNoteResult.error(String.format(PENDING, "la emisión de notas crédito"));
    }

    @Override
    public DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId) {
        return DebitNoteResult.error(String.format(PENDING, "la emisión de notas débito"));
    }

    // --- Rangos de numeración (tablas locales) ---

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
