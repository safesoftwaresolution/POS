package com.sciencebot.pos.billing.internal.mappers;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceDto;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import org.springframework.stereotype.Component;

@Component
public class ElectronicInvoiceMapper {

    public ElectronicInvoiceDto toDto(ElectronicInvoice entity) {
        if (entity == null) {
            return null;
        }

        String status = entity.getStatus() != null ? entity.getStatus().name() : null;

        return new ElectronicInvoiceDto(
                entity.getId(),
                entity.getStoreId(),
                entity.getSaleId(),
                entity.getFactusNumber(),
                entity.getCufe(),
                entity.getQrCode(),
                status,
                entity.getErrorMessage(),
                entity.getPdfUrl(),
                entity.getValidatedAt()
        );
    }
}
