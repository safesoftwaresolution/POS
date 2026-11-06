package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.sciencebot.pos.billing.dto.BillingActivationStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class BillingActivationStatusTest {

    @Test
    void theOnlyStatesAreNotRequestedInSetupActiveAndSuspended() {
        assertEquals(Set.of(NOT_REQUESTED, IN_SETUP, ACTIVE, SUSPENDED), EnumSet.allOf(BillingActivationStatus.class));
    }

    @Test
    void happyPathGoesStepByStep() {
        assertTrue(NOT_REQUESTED.canTransitionTo(IN_SETUP));
        assertTrue(IN_SETUP.canTransitionTo(ACTIVE));
    }

    @Test
    void cannotSkipTheSetup() {
        assertFalse(NOT_REQUESTED.canTransitionTo(ACTIVE));
        assertFalse(NOT_REQUESTED.canTransitionTo(SUSPENDED));
    }

    @Test
    void setupCanBeSuspendedAndActiveAndSuspendedAlternate() {
        assertTrue(IN_SETUP.canTransitionTo(SUSPENDED));
        assertTrue(ACTIVE.canTransitionTo(SUSPENDED));
        assertTrue(SUSPENDED.canTransitionTo(ACTIVE));
    }

    @Test
    void transitionTableIsExactlyTheDocumentedOne() {
        assertEquals(Set.of(IN_SETUP), allowedFrom(NOT_REQUESTED));
        assertEquals(Set.of(ACTIVE, SUSPENDED), allowedFrom(IN_SETUP));
        assertEquals(Set.of(SUSPENDED), allowedFrom(ACTIVE));
        assertEquals(Set.of(ACTIVE), allowedFrom(SUSPENDED));
    }

    @Test
    void noStateTransitionsToItself() {
        for (BillingActivationStatus status : values()) {
            assertFalse(status.canTransitionTo(status), status + " no debe poder pasar a sí mismo");
        }
    }

    @Test
    void onlySuspendedRequiresReason() {
        for (BillingActivationStatus status : values()) {
            assertEquals(status == SUSPENDED, status.requiresReason(), status.name());
        }
    }

    private static Set<BillingActivationStatus> allowedFrom(BillingActivationStatus from) {
        Set<BillingActivationStatus> allowed = EnumSet.noneOf(BillingActivationStatus.class);
        for (BillingActivationStatus target : values()) {
            if (from.canTransitionTo(target)) {
                allowed.add(target);
            }
        }
        return allowed;
    }
}
