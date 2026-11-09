package com.sciencebot.pos.schema;

import com.sciencebot.pos.stores.internal.repositories.StorePurgeDao;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingRegistrationPurgeTest {
    @Test
    void interruptedRegistrationDoesNotPreventPurgingAnOtherwiseDeletableStore() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:purge_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc = JdbcClient.create(ds);
        // H2 cross-connection CHECK bug: remove only this fixture constraint, not foreign keys.
        jdbc.sql("ALTER TABLE stores DROP CONSTRAINT CK_stores_status").update();
        jdbc.sql("INSERT INTO stores(id,name,email,created_at,updated_at) VALUES "
                + "(9000,'Probe','probe@local.test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").update();
        jdbc.sql("INSERT INTO billing_company_registration(store_id,environment,provider_name,identification,"
                + "operation_id,state,processing_started_at,updated_at) VALUES "
                + "(9000,'PRODUCTION','alegra','901234567','probe-operation','UNCERTAIN',"
                + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").update();

        new TransactionTemplate(new DataSourceTransactionManager(ds))
                .executeWithoutResult(status -> new StorePurgeDao(jdbc).purge(9000L));

        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM stores WHERE id=9000").query(Long.class).single());
        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM billing_company_registration WHERE store_id=9000")
                .query(Long.class).single());
    }
}
