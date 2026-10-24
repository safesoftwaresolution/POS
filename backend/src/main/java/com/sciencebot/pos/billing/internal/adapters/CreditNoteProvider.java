    package com.sciencebot.pos.billing.internal.adapters;

    import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
    import com.sciencebot.pos.billing.internal.adapters.dto.CreditNoteResult;

    public interface CreditNoteProvider {


        String getProviderName();

        CreditNoteResult emitCreditNote(CreateCreditNoteRequest request, Long storeId);

        default byte[] downloadCreditNotePDF(String creditNoteNumber, Long storeId) {
            throw new UnsupportedOperationException("downloadCreditNotePDF no soportado por " + getProviderName());
        }

        default byte[] downloadCreditNoteXML(String creditNoteNumber, Long storeId) {
            throw new UnsupportedOperationException("downloadCreditNoteXML no soportado por " + getProviderName());
        }

        default boolean deleteUnvalidatedCreditNote(String referenceCode, Long storeId) {
            throw new UnsupportedOperationException("deleteUnvalidatedCreditNote no soportado por " + getProviderName());
        }
    }
