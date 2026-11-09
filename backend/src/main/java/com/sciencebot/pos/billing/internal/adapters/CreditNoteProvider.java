    package com.sciencebot.pos.billing.internal.adapters;

    import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
    import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import java.util.List;

    public interface CreditNoteProvider {


        String getProviderName();

        CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId);

        default byte[] downloadCreditNotePDF(String creditNoteNumber, Long storeId) {
            throw new UnsupportedOperationException("downloadCreditNotePDF no soportado por " + getProviderName());
        }

        default byte[] downloadCreditNoteXML(String creditNoteNumber, Long storeId) {
            throw new UnsupportedOperationException("downloadCreditNoteXML no soportado por " + getProviderName());
        }
            default CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId,
                BillingDocumentContext context, List<InvoiceItemData> canonicalItems) {
            return emitCreditNote(request, storeId);
        }

        default CreditNoteResult retryCreditNote(String referenceCode, BillingDocumentContext context) {
            throw new UnsupportedOperationException("La recuperación de notas no está disponible para " + getProviderName());
        }

        default CreditNoteResult queryCreditNote(String providerDocumentId, BillingDocumentContext context) {
            throw new UnsupportedOperationException("La consulta de notas no está disponible para " + getProviderName());
        }

        default byte[] downloadCreditNotePDF(String number, String providerId, Long storeId, BillingDocumentContext context) {
            return downloadCreditNotePDF(number, storeId);
        }

        default byte[] downloadCreditNoteXML(String number, String providerId, Long storeId, BillingDocumentContext context) {
            return downloadCreditNoteXML(number, storeId);
        }
}
