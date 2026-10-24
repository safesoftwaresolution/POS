package com.sciencebot.pos.stores;

import com.sciencebot.pos.stores.internal.entities.PlanEntity;
import com.sciencebot.pos.stores.internal.entities.StoreEntity;
import com.sciencebot.pos.stores.internal.repositories.PlanRepository;
import com.sciencebot.pos.stores.internal.repositories.StoreRepository;
import com.sciencebot.pos.stores.internal.services.PlanUserQuotaPolicy;
import com.sciencebot.pos.users.UserQuota;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class PlanUserQuotaPolicyTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final PlanUserQuotaPolicy policy = new PlanUserQuotaPolicy(storeRepository, planRepository);

    private StoreEntity storeOnPlan(String planCode) {
        StoreEntity store = new StoreEntity();
        store.setId(1L);
        store.setPlanCode(planCode);
        return store;
    }

    private PlanEntity plan(String code, String name, int additional) {
        PlanEntity plan = new PlanEntity();
        plan.setCode(code);
        plan.setName(name);
        plan.setMaxAdditionalUsers(additional);
        return plan;
    }

    @Test
    void quotaFor_CountsTheAdministratorPlusTheAdditionalUsersOfThePlan() {
        when(storeRepository.findById(1L)).thenReturn(Optional.of(storeOnPlan("BASIC")));
        when(planRepository.findById("BASIC")).thenReturn(Optional.of(plan("BASIC", "Básico", 1)));

        UserQuota quota = policy.quotaFor(1L).orElseThrow();

        assertEquals("BASIC", quota.planCode());
        assertEquals("Básico", quota.planName());
        assertEquals(2, quota.maxActiveUsers());
    }

    @Test
    void newStoreDefaultsToTheBasicPlan() {
        assertEquals("BASIC", new StoreEntity().getPlanCode());
    }

    @Test
    void quotaFor_UnknownStoreUnknownPlanOrNoStore_IsEmpty() {
        when(storeRepository.findById(9L)).thenReturn(Optional.empty());
        assertTrue(policy.quotaFor(9L).isEmpty());

        when(storeRepository.findById(1L)).thenReturn(Optional.of(storeOnPlan("GHOST")));
        when(planRepository.findById("GHOST")).thenReturn(Optional.empty());
        assertTrue(policy.quotaFor(1L).isEmpty());

        assertTrue(policy.quotaFor(null).isEmpty());
        verify(storeRepository, never()).findById(isNull());
    }
}
