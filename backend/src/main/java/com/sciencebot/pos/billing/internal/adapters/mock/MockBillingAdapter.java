package com.sciencebot.pos.billing.internal.adapters.mock;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.internal.adapters.ElectronicInvoicingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptador Mock para facturación electrónica simulada en memoria.
 * Permite ejecutar tests y trabajar en modo local/offline sin dependencias de red ni credenciales externas.
 */
@Component("mockBillingProvider")
@ConditionalOnProperty(name = "billing.provider", havingValue = "mock")
public class MockBillingAdapter implements ElectronicInvoicingProvider {

    @Override
    public String getProviderName() {
        return "mock";
    }

    // --- Cuenta del local: el mock no necesita credenciales ni hace llamadas de red ---

    @Override
    public boolean isAccountConfigured(Long storeId, BillingEnvironment environment) {
        return true;
    }

    @Override
    public void verifyConnection(Long storeId, BillingEnvironment environment) {
        // Nada que verificar: no hay proveedor externo.
    }

    @Override
    public void onAccountChanged(Long storeId) {
        // Sin caché que invalidar.
    }

    @Override
    public String displayName() {
        return "Mock";
    }

    @Override
    public InvoiceResult emitInvoice(InvoiceRequest request) {
        if (request == null || request.saleId() == null) {
            return InvoiceResult.error("Solicitud de factura nula o sin ID de venta");
        }

        String legalNumber = "SETP-MOCK-" + String.format("%06d", request.saleId());
        String cufe = UUID.nameUUIDFromBytes(("MOCK-CUFE-" + request.saleId()).getBytes()).toString().replace("-", "") + "abcdef0123456789";
        String qrCode = "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=" + cufe;
        String pdfUrl = "https://api-mock.local/v1/bills/" + legalNumber + "/pdf";

        return InvoiceResult.success(legalNumber, cufe, qrCode, pdfUrl);
    }

    @Override
    public InvoiceResult queryInvoice(String legalNumber) {
        String cufe = UUID.randomUUID().toString().replace("-", "");
        String qrCode = "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=" + cufe;
        String pdfUrl = "https://api-mock.local/v1/bills/" + legalNumber + "/pdf";

        return InvoiceResult.success(legalNumber, cufe, qrCode, pdfUrl);
    }

    @Override
    public byte[] downloadInvoicePDF(String legalNumber) {
        return ("%PDF-1.4 Mock PDF content for " + legalNumber).getBytes();
    }

    @Override
    public byte[] downloadInvoiceXML(String legalNumber) {
        return ("<Invoice><LegalNumber>" + legalNumber + "</LegalNumber></Invoice>").getBytes();
    }

    @Override
    public boolean deleteUnvalidatedInvoice(String legalNumber) {
        return true;
    }

    @Override
    public boolean sendInvoiceEmail(String legalNumber, String email) {
        return true;
    }

    @Override
    public java.util.List<com.sciencebot.pos.billing.dto.NumberingRangeDto> queryDianNumberingRanges() {
        return java.util.List.of(
                new com.sciencebot.pos.billing.dto.NumberingRangeDto(
                        1L, "01", "SETP", "18764000001234", 1L, 5000L, 1L,
                        "2026-01-01", "2027-01-01", "fc8eac422eba16e22ffd8c6f94b3f40a6e381edf", true
                )
        );
    }

    @Override
    public java.util.List<com.sciencebot.pos.billing.dto.NumberingRangeDto> listNumberingRanges() {
        return java.util.List.of(
                new com.sciencebot.pos.billing.dto.NumberingRangeDto(
                        1L, "01", "SETP", "18764000001234", 1L, 5000L, 1L,
                        "2026-01-01", "2027-01-01", "fc8eac422eba16e22ffd8c6f94b3f40a6e381edf", true
                )
        );
    }

    @Override
    public com.sciencebot.pos.billing.dto.NumberingRangeDto getNumberingRange(Long numberingRangeId) {
        return new com.sciencebot.pos.billing.dto.NumberingRangeDto(
                numberingRangeId != null ? numberingRangeId : 1L,
                "01", "SETP", "18764000001234", 1L, 5000L, 1L,
                "2026-01-01", "2027-01-01", "fc8eac422eba16e22ffd8c6f94b3f40a6e381edf", true
        );
    }

    @Override
    public com.sciencebot.pos.billing.dto.NumberingRangeDto createNumberingRange(com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest request) {
        return new com.sciencebot.pos.billing.dto.NumberingRangeDto(
                100L,
                request.document(),
                request.prefix(),
                request.resolutionNumber(),
                1L,
                5000L,
                request.current(),
                "2026-01-01",
                "2027-01-01",
                "fc8eac422eba16e22ffd8c6f94b3f40a6e381edf",
                true
        );
    }

    @Override
    public void deleteNumberingRange(Long numberingRangeId) {
        // Nada que borrar: el mock no guarda rangos.
    }

    @Override
    public com.sciencebot.pos.billing.dto.NumberingRangeDto toggleNumberingRangeStatus(Long numberingRangeId) {
        return getNumberingRange(numberingRangeId);
    }

    @Override
    public com.sciencebot.pos.billing.dto.NumberingRangeDto updateNumberingRangeCurrent(Long numberingRangeId, Long current) {
        return new com.sciencebot.pos.billing.dto.NumberingRangeDto(
                numberingRangeId != null ? numberingRangeId : 1L,
                "01", "SETP", "18764000001234", 1L, 5000L, current,
                "2026-01-01", "2027-01-01", "fc8eac422eba16e22ffd8c6f94b3f40a6e381edf", true
        );
    }

    // --- Notas Crédito Mock ---

    @Override
    public com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult emitCreditNote(
            com.sciencebot.pos.billing.dto.CreateCreditNoteRequest request, Long storeId) {
        if (request == null || request.referenceCode() == null) {
            return com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult.error("Solicitud de nota crédito nula o sin referencia");
        }

        String legalNumber = "NC-MOCK-" + Math.abs(request.referenceCode().hashCode() % 100000);
        String cude = UUID.nameUUIDFromBytes(("MOCK-CUDE-" + request.referenceCode()).getBytes()).toString().replace("-", "") + "abcdef0123456789";
        String qrCode = "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=" + cude;
        String pdfUrl = "https://api-mock.local/v2/credit-notes/" + legalNumber + "/pdf";

        return com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult.success(legalNumber, cude, qrCode, pdfUrl);
    }

    @Override
    public byte[] downloadCreditNotePDF(String creditNoteNumber, Long storeId) {
        return ("%PDF-1.4 Mock PDF content for Credit Note " + creditNoteNumber).getBytes();
    }

    @Override
    public byte[] downloadCreditNoteXML(String creditNoteNumber, Long storeId) {
        return ("<CreditNote><LegalNumber>" + creditNoteNumber + "</LegalNumber></CreditNote>").getBytes();
    }

    @Override
    public boolean deleteUnvalidatedCreditNote(String referenceCode, Long storeId) {
        return true;
    }

    // --- Notas Débito Mock ---

    @Override
    public DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId) {
        if (request == null || request.referenceCode() == null) {
            return DebitNoteResult.error("Solicitud de nota débito nula o sin referencia");
        }

        String legalNumber = "ND-MOCK-" + Math.abs(request.referenceCode().hashCode() % 100000);
        String cude = UUID.nameUUIDFromBytes(("MOCK-CUDE-ND-" + request.referenceCode()).getBytes()).toString().replace("-", "") + "abcdef0123456789";
        String qrCode = "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=" + cude;
        String pdfUrl = "https://api-mock.local/v2/debit-notes/" + legalNumber + "/pdf";

        return DebitNoteResult.success(legalNumber, cude, qrCode, pdfUrl);
    }

    @Override
    public byte[] downloadDebitNotePDF(String debitNoteNumber, Long storeId) {
        return ("%PDF-1.4 Mock PDF content for Debit Note " + debitNoteNumber).getBytes();
    }

    @Override
    public byte[] downloadDebitNoteXML(String debitNoteNumber, Long storeId) {
        return ("<DebitNote><LegalNumber>" + debitNoteNumber + "</LegalNumber></DebitNote>").getBytes();
    }

    @Override
    public boolean deleteUnvalidatedDebitNote(String referenceCode, Long storeId) {
        return true;
    }
}
