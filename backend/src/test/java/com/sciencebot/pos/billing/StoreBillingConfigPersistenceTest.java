package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.api.*;
import com.sciencebot.pos.billing.dto.*;
import com.sciencebot.pos.billing.exception.*;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica con Hibernate y H2 reales que los secretos quedan cifrados en la columna. */
@SpringBootTest
@Transactional
class StoreBillingConfigPersistenceTest {

    @Autowired
    private StoreBillingConfigRepository repository;

    @Autowired
    private EntityManager em;

    @Test
    void secretsAreEncryptedInTheDatabaseAndDecryptedWhenRead() {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(9001L);
        config.getSandbox().setClientId("client-visible");
        config.getSandbox().setClientSecret("secreto-sandbox");
        config.getSandbox().setUsername("user@x.co");
        config.getSandbox().setPassword("clave-sandbox");
        config.getProduction().setClientSecret("secreto-prod");
        repository.saveAndFlush(config);

        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT sandbox_client_id, sandbox_client_secret, sandbox_password, production_client_secret "
                        + "FROM store_billing_config WHERE store_id = 9001").getSingleResult();

        assertEquals("client-visible", row[0]);
        for (int i = 1; i <= 3; i++) {
            String stored = (String) row[i];
            assertTrue(stored.startsWith("v1:"), "columna " + i + " debe estar cifrada");
            assertFalse(stored.contains("secreto") || stored.contains("clave"));
        }

        em.clear();
        StoreBillingConfig reloaded = repository.findById(9001L).orElseThrow();
        assertEquals("secreto-sandbox", reloaded.getSandbox().getClientSecret());
        assertEquals("clave-sandbox", reloaded.getSandbox().getPassword());
        assertEquals("secreto-prod", reloaded.getProduction().getClientSecret());
        assertEquals(BillingActivationStatus.NOT_REQUESTED, reloaded.getStatus());
        assertEquals(BillingEnvironment.SANDBOX, reloaded.getEnvironment());
        assertNotNull(reloaded.getCreatedAt());
    }
}
