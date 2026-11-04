package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StoreBillingConfigCompanyIdTest {

    @Test
    void companyIdFor_IsEmptyUntilAssignedAndKeepsEachEnvironmentApart() {
        StoreBillingConfig config = new StoreBillingConfig();
        assertNull(config.companyIdFor(BillingEnvironment.SANDBOX));
        assertNull(config.companyIdFor(BillingEnvironment.PRODUCTION));

        config.assignCompanyId(BillingEnvironment.SANDBOX, "01M6KH7R1QZQK687BNMX5BBXZC");

        assertEquals("01M6KH7R1QZQK687BNMX5BBXZC", config.companyIdFor(BillingEnvironment.SANDBOX));
        assertNull(config.companyIdFor(BillingEnvironment.PRODUCTION));
    }

    @Test
    void assignCompanyId_TrimsTheValue() {
        StoreBillingConfig config = new StoreBillingConfig();
        config.assignCompanyId(BillingEnvironment.PRODUCTION, "  abc123  ");
        assertEquals("abc123", config.companyIdFor(BillingEnvironment.PRODUCTION));
    }

    @Test
    void assignCompanyId_RejectsBlankAndNull() {
        StoreBillingConfig config = new StoreBillingConfig();
        assertThrows(IllegalArgumentException.class, () -> config.assignCompanyId(BillingEnvironment.SANDBOX, "  "));
        assertThrows(IllegalArgumentException.class, () -> config.assignCompanyId(BillingEnvironment.SANDBOX, null));
        assertNull(config.companyIdFor(BillingEnvironment.SANDBOX));
    }

    @Test
    void assignCompanyId_RejectsAValueLongerThanTheColumn() {
        StoreBillingConfig config = new StoreBillingConfig();
        assertThrows(IllegalArgumentException.class,
                () -> config.assignCompanyId(BillingEnvironment.SANDBOX, "x".repeat(65)));
    }
}
