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
 * Adaptador de Alegra (proveedor tecnológico DIAN). Por ahora cubre la cuenta (configuración, prueba de conexión
 * y caché de la empresa) y los rangos de numeración locales; emitir facturas y notas llega en FEAT-025 y 026, y
 * mientras tanto devuelve un resultado de error claro en lugar de fingir una emisión.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraBillingAdapter implements ElectronicInvoicingProvider {

    private static final String PENDING = "Alegra: %s no está implementada todavía";

    private final AlegraAccessResolver accessResolver;
    private final AlegraCompanyProvider companyProvider;
    private final AlegraNumberingRangeClient rangeClient;

    public AlegraBillingAdapter(AlegraAccessResolver accessResolver,
                                AlegraCompanyProvider companyProvider,
                                AlegraNumberingRangeClient rangeClient) {
        this.accessResolver = accessResolver;
        this.companyProvider = companyProvider;
        this.rangeClient = rangeClient;
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

    // --- Facturas y notas (FEAT-025 y 026) ---

    @Override
    public InvoiceResult emitInvoice(InvoiceRequest request) {
        return InvoiceResult.error(String.format(PENDING, "la emisión de facturas"));
    }

    @Override
    public InvoiceResult queryInvoice(String legalNumber) {
        return InvoiceResult.error(String.format(PENDING, "la consulta de facturas"));
    }

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
