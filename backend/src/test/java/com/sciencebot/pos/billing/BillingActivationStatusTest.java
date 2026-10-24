package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.sciencebot.pos.billing.dto.BillingActivationStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class BillingActivationStatusTest {

    @Test
    void happyPathGoesStepByStep() {
        assertTrue(NOT_REQUESTED.canTransitionTo(DOCUMENTS_SUBMITTED));
        assertTrue(DOCUMENTS_SUBMITTED.canTransitionTo(AWAITING_PROVIDER));
        assertTrue(AWAITING_PROVIDER.canTransitionTo(ACTIVE));
    }

    @Test
    void cannotSkipSteps() {
        assertFalse(NOT_REQUESTED.canTransitionTo(ACTIVE));
        assertFalse(NOT_REQUESTED.canTransitionTo(AWAITING_PROVIDER));
        assertFalse(DOCUMENTS_SUBMITTED.canTransitionTo(ACTIVE));
    }

    @Test
    void rejectedCanBeResubmittedAndSuspendedCanBeReactivated() {
        assertTrue(DOCUMENTS_SUBMITTED.canTransitionTo(REJECTED));
        assertTrue(AWAITING_PROVIDER.canTransitionTo(REJECTED));
        assertTrue(REJECTED.canTransitionTo(DOCUMENTS_SUBMITTED));
        assertTrue(ACTIVE.canTransitionTo(SUSPENDED));
        assertTrue(SUSPENDED.canTransitionTo(ACTIVE));
    }

    @Test
    void noStateTransitionsToItself() {
        for (BillingActivationStatus status : values()) {
            assertFalse(status.canTransitionTo(status), status + " no debe poder pasar a sí mismo");
        }
    }

    @Test
    void activeCanOnlyBeSuspended() {
        Set<BillingActivationStatus> allowed = EnumSet.noneOf(BillingActivationStatus.class);
        for (BillingActivationStatus target : values()) {
            if (ACTIVE.canTransitionTo(target)) {
                allowed.add(target);
            }
        }
        assertEquals(Set.of(SUSPENDED), allowed);
    }

    @Test
    void onlyRejectedAndSuspendedRequireReason() {
        for (BillingActivationStatus status : values()) {
            assertEquals(status == REJECTED || status == SUSPENDED, status.requiresReason(), status.name());
        }
    }
}
