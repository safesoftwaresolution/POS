package com.sciencebot.pos.finance.internal.mappers;

import com.sciencebot.pos.finance.FixedCostDto;
import com.sciencebot.pos.finance.MovementDto;
import com.sciencebot.pos.finance.internal.entities.FinanceMovement;
import com.sciencebot.pos.finance.internal.entities.FixedCost;
import org.springframework.stereotype.Component;

@Component
public class FinanceMapper {

    public MovementDto toDto(FinanceMovement m) {
        return new MovementDto(m.getId(), m.getType(), m.getCategory(), m.getDescription(), m.getAmount(),
                m.getPaymentMethod(), m.getMovementDate(), m.getUsername());
    }

    public FixedCostDto toDto(FixedCost c) {
        return new FixedCostDto(c.getId(), c.getName(), c.getMonthlyAmount(), c.getDueDay(), c.isActive());
    }
}
