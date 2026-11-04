package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraAccessResolver;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraBillingAdapter;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraClientHelper;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraCompanyProvider;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberingRangeClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Con el proveedor por defecto (factus) ningun bean de Alegra existe: no hay credenciales ni HTTP de Alegra activos. */
@SpringBootTest
class AlegraBeansNotRegisteredTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void noAlegraBeanIsRegisteredWhenTheProviderIsNotAlegra() {
        for (Class<?> type : new Class<?>[]{AlegraBillingAdapter.class, AlegraAccessResolver.class, AlegraClientHelper.class,
                AlegraCompanyProvider.class, AlegraNumberAllocator.class, AlegraNumberingRangeClient.class}) {
            assertEquals(0, context.getBeanNamesForType(type).length, type.getSimpleName());
        }
    }
}
