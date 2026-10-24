package com.sciencebot.pos.billing.internal.mappers;

import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import com.sciencebot.pos.billing.internal.entities.ElectronicCreditNote;
import org.springframework.stereotype.Component;

@Component
public class ElectronicCreditNoteMapper {

    public CreditNoteDTO toDto(ElectronicCreditNote entity) {
        if (entity == null) {
            return null;
        }

        String status = entity.getStatus() != null ? entity.getStatus().name() : null;

        return new CreditNoteDTO(
                entity.getId(),
                entity.getStoreId(),
                entity.getSaleId(),
                entity.getReferenceCode(),
                entity.getBillNumber(),
                entity.getCreditNoteNumber(),
                entity.getCorrectionConceptCode(),
                entity.getCustomizationId(),
                entity.getCude(),
                entity.getQrCode(),
                status,
                entity.getErrorMessage(),
                entity.getPdfUrl(),
                entity.getValidatedAt(),
                entity.getCreatedAt()
        );
    }
}
