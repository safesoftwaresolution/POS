package com.sciencebot.pos.billing.internal.adapters;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;
import com.sciencebot.pos.billing.internal.adapters.dto.BillingDocumentContext;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceItemData;
import java.util.List;

public interface DebitNoteProvider {

    String getProviderName();

    DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId);

    default byte[] downloadDebitNotePDF(String debitNoteNumber, Long storeId) {
        throw new UnsupportedOperationException("downloadDebitNotePDF no soportado por " + getProviderName());
    }

    default byte[] downloadDebitNoteXML(String debitNoteNumber, Long storeId) {
        throw new UnsupportedOperationException("downloadDebitNoteXML no soportado por " + getProviderName());
    }
        default DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId,
                BillingDocumentContext context, List<InvoiceItemData> canonicalItems) {
            return emitDebitNote(request, storeId);
        }

        default DebitNoteResult retryDebitNote(String referenceCode, BillingDocumentContext context) {
            throw new UnsupportedOperationException("La recuperación de notas no está disponible para " + getProviderName());
        }

        default DebitNoteResult queryDebitNote(String providerDocumentId, BillingDocumentContext context) {
            throw new UnsupportedOperationException("La consulta de notas no está disponible para " + getProviderName());
        }

        default byte[] downloadDebitNotePDF(String number, String providerId, Long storeId, BillingDocumentContext context) {
            return downloadDebitNotePDF(number, storeId);
        }

        default byte[] downloadDebitNoteXML(String number, String providerId, Long storeId, BillingDocumentContext context) {
            return downloadDebitNoteXML(number, storeId);
        }
}