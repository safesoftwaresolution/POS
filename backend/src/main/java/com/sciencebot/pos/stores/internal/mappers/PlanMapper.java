package com.sciencebot.pos.stores.internal.mappers;

import com.sciencebot.pos.stores.PlanDto;
import com.sciencebot.pos.stores.internal.entities.PlanEntity;
import org.springframework.stereotype.Component;

@Component
public class PlanMapper {

    public PlanDto toDto(PlanEntity plan) {
        return new PlanDto(plan.getCode(), plan.getName(), plan.getMaxAdditionalUsers(),
                1 + plan.getMaxAdditionalUsers(), plan.getMonthlyPrice(), plan.getYearlyPrice());
    }
}
