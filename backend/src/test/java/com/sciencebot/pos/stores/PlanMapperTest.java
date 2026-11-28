package com.sciencebot.pos.stores;

import com.sciencebot.pos.stores.internal.entities.PlanEntity;
import com.sciencebot.pos.stores.internal.mappers.PlanMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlanMapperTest {

    private final PlanMapper mapper = new PlanMapper();

    private PlanEntity plan(BigDecimal monthly, BigDecimal yearly) {
        PlanEntity p = new PlanEntity();
        p.setCode("PRO");
        p.setName("Pro");
        p.setMaxAdditionalUsers(10);
        p.setSortOrder(3);
        p.setMonthlyPrice(monthly);
        p.setYearlyPrice(yearly);
        return p;
    }

    @Test
    void toDto_CopiesPricesAndComputesActiveUserCap() {
        PlanDto dto = mapper.toDto(plan(new BigDecimal("99000.00"), new BigDecimal("990000.00")));

        assertEquals("PRO", dto.code());
        assertEquals("Pro", dto.name());
        assertEquals(10, dto.maxAdditionalUsers());
        assertEquals(11, dto.maxActiveUsers());
        assertEquals(new BigDecimal("99000.00"), dto.monthlyPrice());
        assertEquals(new BigDecimal("990000.00"), dto.yearlyPrice());
    }

    @Test
    void toDto_UndefinedPricesStayNull() {
        PlanDto dto = mapper.toDto(plan(null, null));

        assertNull(dto.monthlyPrice());
        assertNull(dto.yearlyPrice());
    }
}
