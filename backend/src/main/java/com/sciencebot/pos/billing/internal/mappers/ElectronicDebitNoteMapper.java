package com.sciencebot.pos.billing.internal.mappers;

import org.springframework.stereotype.Component;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import com.sciencebot.pos.billing.internal.entities.ElectronicDebitNote;

@Component 
public class ElectronicDebitNoteMapper {

    public DebitNoteDTO toDto(ElectronicDebitNote entity) {
        if (entity == null) {
            return null;
        }

        String status = entity.getStatus() != null ? entity.getStatus().name() : null;

        return new DebitNoteDTO(
                entity.getId(),
                entity.getStoreId(),
                entity.getSaleId(),
                entity.getReferenceCode(),
                entity.getBillNumber(),
                entity.getDebitNoteNumber(),
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
