package com.sciencebot.pos.audit;

import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Con Hibernate y H2 reales: los cambios se guardan como JSON, se leen igual y quedan aislados por local. */
@SpringBootTest
@Transactional
class AuditServiceIntegrationTest {

    @Autowired
    private AuditFacade auditFacade;

    @BeforeEach
    void setUp() {
        TenantContext.setStoreId(9201L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void changesRoundTripAndHistoryIsNewestFirst() {
        auditFacade.record("CUSTOMER", 5L, AuditAction.CREATE, 7L, "ana",
                Map.of("fullName", new FieldChange(null, "Maria")));
        auditFacade.record("CUSTOMER", 5L, AuditAction.UPDATE, 8L, "luis",
                Map.of("fullName", new FieldChange("Maria", "Maria Lopez"), "phone", new FieldChange(null, 3001234567L)));

        var history = auditFacade.history("CUSTOMER", 5L, PageRequest.of(0, 10)).getContent();

        assertEquals(2, history.size());
        AuditEntryDto latest = history.get(0);
        assertEquals(AuditAction.UPDATE, latest.action());
        assertEquals("luis", latest.username());
        assertEquals("Maria", latest.changes().get("fullName").from());
        assertEquals("Maria Lopez", latest.changes().get("fullName").to());
        assertNull(latest.changes().get("phone").from());
        assertNotNull(latest.createdAt());
        assertEquals(AuditAction.CREATE, history.get(1).action());
    }

    @Test
    void anEntryWithoutChangesIsReadBackAsEmpty() {
        auditFacade.record("CUSTOMER", 6L, AuditAction.DELETE, 1L, "boss", Map.of());

        var entry = auditFacade.history("CUSTOMER", 6L, PageRequest.of(0, 10)).getContent().get(0);

        assertEquals(AuditAction.DELETE, entry.action());
        assertTrue(entry.changes().isEmpty());
    }

    @Test
    void historyOnlyShowsTheCurrentStoresEntries() {
        auditFacade.record("CUSTOMER", 5L, AuditAction.CREATE, 7L, "ana", Map.of());

        TenantContext.setStoreId(9202L);
        assertTrue(auditFacade.history("CUSTOMER", 5L, PageRequest.of(0, 10)).isEmpty());
    }

    @Test
    void recordingWithoutAStoreIsRefused() {
        TenantContext.clear();

        assertThrows(IllegalStateException.class,
                () -> auditFacade.record("CUSTOMER", 5L, AuditAction.CREATE, 7L, "ana", Map.of()));
    }
}
