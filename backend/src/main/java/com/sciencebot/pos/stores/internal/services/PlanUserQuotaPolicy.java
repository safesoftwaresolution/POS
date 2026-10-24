package com.sciencebot.pos.stores.internal.services;

import com.sciencebot.pos.stores.internal.entities.PlanEntity;
import com.sciencebot.pos.stores.internal.repositories.PlanRepository;
import com.sciencebot.pos.stores.internal.repositories.StoreRepository;
import com.sciencebot.pos.users.UserQuota;
import com.sciencebot.pos.users.UserQuotaPolicy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Tope de usuarios por plan: el administrador del local cuenta como uno, y el plan añade
 * {@code max_additional_users} más. Implementa el puerto {@link UserQuotaPolicy} del módulo users.
 */
@Component
@Transactional(readOnly = true)
public class PlanUserQuotaPolicy implements UserQuotaPolicy {

    private final StoreRepository storeRepository;
    private final PlanRepository planRepository;

    public PlanUserQuotaPolicy(StoreRepository storeRepository, PlanRepository planRepository) {
        this.storeRepository = storeRepository;
        this.planRepository = planRepository;
    }

    @Override
    public Optional<UserQuota> quotaFor(Long storeId) {
        if (storeId == null) {
            return Optional.empty();
        }
        return storeRepository.findById(storeId)
                .flatMap(store -> planRepository.findById(store.getPlanCode()))
                .map(PlanUserQuotaPolicy::toQuota);
    }

    static UserQuota toQuota(PlanEntity plan) {
        return new UserQuota(plan.getCode(), plan.getName(), 1 + plan.getMaxAdditionalUsers());
    }
}
