package com.sciencebot.pos.credit.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.credit.internal.repositories.CreditAccountRepository;
import com.sciencebot.pos.customers.CustomerDeleteValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Un cliente que todavía debe dinero no se puede dar de baja. Solo usa el repositorio para no formar un ciclo con customers. */
@Component
public class CreditCustomerDeleteGuard implements CustomerDeleteValidator {

    private final CreditAccountRepository accountRepository;

    public CreditCustomerDeleteGuard(CreditAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void validateBeforeDelete(Long customerId) {
        Long storeId = TenantContext.getStoreId();
        if (storeId != null && accountRepository.existsByStoreIdAndCustomerIdAndBalanceGreaterThan(storeId, customerId, BigDecimal.ZERO)) {
            throw new IllegalStateException("No se puede eliminar un cliente que tiene deuda de fiado pendiente");
        }
    }
}
