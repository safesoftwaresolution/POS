package com.sciencebot.pos.billing.internal.adapters;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.DebitNoteResult;

public interface DebitNoteProvider {

    String getProviderName();

    DebitNoteResult emitDebitNote(CreateDebitNoteRequest request, Long storeId);

    default byte[] downloadDebitNotePDF(String debitNoteNumber, Long storeId) {
        throw new UnsupportedOperationException("downloadDebitNotePDF no soportado por " + getProviderName());
    }

    default byte[] downloadDebitNoteXML(String debitNoteNumber, Long storeId) {
        throw new UnsupportedOperationException("downloadDebitNoteXML no soportado por " + getProviderName());
    }

    default boolean deleteUnvalidatedDebitNote(String referenceCode, Long storeId) {
        throw new UnsupportedOperationException("deleteUnvalidatedDebitNote no soportado por " + getProviderName());
    }
}