package com.sciencebot.pos.billing.internal.mappers;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import org.springframework.stereotype.Component;

@Component
public class BillingActivationMapper {

    public BillingActivationDto toDto(StoreBillingConfig entity) {
        if (entity == null) {
            return null;
        }
        return new BillingActivationDto(
                entity.getStoreId(),
                entity.getStatus(),
                entity.getStatusReason(),
                entity.getEnvironment(),
                entity.getSandbox().isComplete(),
                entity.getProduction().isComplete(),
                entity.getSandbox().getNumberingRangeId(),
                entity.getProduction().getNumberingRangeId(),
                entity.getUpdatedAt()
        );
    }

    /** Estado de un local que todavía no pidió facturación electrónica (no hay fila guardada). */
    public BillingActivationDto notRequested(Long storeId) {
        return new BillingActivationDto(
                storeId,
                BillingActivationStatus.NOT_REQUESTED,
                null,
                BillingEnvironment.SANDBOX,
                false,
                false,
                null,
                null,
                null
        );
    }
}
