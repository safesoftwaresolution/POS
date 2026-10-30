package com.sciencebot.pos.credit.internal.mappers;

import com.sciencebot.pos.credit.CreditMovementDto;
import com.sciencebot.pos.credit.internal.entities.CreditMovement;
import org.springframework.stereotype.Component;

@Component
public class CreditMovementMapper {

    public CreditMovementDto toDto(CreditMovement m) {
        if (m == null) {
            return null;
        }
        return new CreditMovementDto(m.getId(), m.getCustomerId(), m.getType(), m.getAmount(), m.getBalanceAfter(),
                m.getSaleId(), m.getPaymentMethod(), m.getDueDate(), m.getNote(), m.getUsername(), m.getCreatedAt());
    }
}
