package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.internal.adapters.NumberingRangeProvider;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberingRangeClient;
import com.sciencebot.pos.billing.internal.entities.NumberingRangeAudit;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.repositories.DocumentNumberAllocationRepository;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeAuditRepository;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.NumberingRangeServiceImpl;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El servicio real de rangos (con sus guardas) funcionando sobre el cliente local de Alegra:
 * crear, un solo rango activo por documento, el rango configurado no se desactiva y el consecutivo no retrocede.
 */
@SpringBootTest
class NumberingRangeServiceWithLocalRangesTest {

    @Autowired
    private NumberingRangeRepository rangeRepository;
    @Autowired
    private DocumentNumberAllocationRepository allocationRepository;
    @Autowired
    private StoreBillingConfigRepository configRepository;
    @Autowired
    private NumberingRangeAuditRepository auditRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private NumberingRangeServiceImpl service;
    private Long storeId;

    @BeforeEach
    void setUp() {
        AlegraNumberingRangeClient client = new AlegraNumberingRangeClient(
                rangeRepository, allocationRepository, configRepository, transactionManager);
        service = new NumberingRangeServiceImpl(List.of(asProvider(client)), configRepository, auditRepository);
        ReflectionTestUtils.setField(service, "activeProviderName", "alegra");

        storeId = 300_000L + (System.nanoTime() % 1_000_000L);
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(storeId);
        config.setEnvironment(BillingEnvironment.SANDBOX);
        configRepository.save(config);
        TenantContext.setStoreId(storeId);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        auditRepository.deleteAll(auditRepository.findAll().stream()
                .filter(a -> a.getStoreId().equals(storeId)).toList());
        rangeRepository.deleteAll(rangeRepository.findAll().stream()
                .filter(r -> r.getStoreId().equals(storeId)).toList());
        configRepository.deleteById(storeId);
    }

    /** El cliente local no implementa el puerto (lo hará el adaptador de FEAT-024): aquí se envuelve solo para el test. */
    private static NumberingRangeProvider asProvider(AlegraNumberingRangeClient client) {
        return new NumberingRangeProvider() {
            @Override public String getProviderName() { return "alegra"; }
            @Override public List<NumberingRangeDto> listNumberingRanges() { return client.listNumberingRanges(); }
            @Override public NumberingRangeDto getNumberingRange(Long id) { return client.getNumberingRange(id); }
            @Override public NumberingRangeDto createNumberingRange(CreateNumberingRangeRequest r) { return client.createNumberingRange(r); }
            @Override public void deleteNumberingRange(Long id) { client.deleteNumberingRange(id); }
            @Override public NumberingRangeDto toggleNumberingRangeStatus(Long id) { return client.toggleNumberingRangeStatus(id); }
            @Override public NumberingRangeDto updateNumberingRangeCurrent(Long id, Long c) { return client.updateNumberingRangeCurrent(id, c); }
        };
    }

    private static CreateNumberingRangeRequest request(String document, String prefix, String resolution) {
        return new CreateNumberingRangeRequest(document, prefix, resolution, 1L, 1L, 1000L,
                "2019-01-19", "2030-01-19", "technical-key");
    }

    @Test
    void firstRangeBecomesTheStoresDefaultAndASecondActiveOneForTheSameDocumentIsRefused() {
        NumberingRangeDto first = service.createNumberingRange(request("01", "SETP", "111"));

        assertEquals(first.id().intValue(),
                configRepository.findById(storeId).orElseThrow().getSandboxNumberingRangeId());
        assertThrows(IllegalStateException.class, () -> service.createNumberingRange(request("01", "SETQ", "222")));
        assertDoesNotThrow(() -> service.createNumberingRange(request("91", "NC", "333")), "otro documento si puede tener su rango");
        assertEquals(2, service.listNumberingRanges().size());
    }

    @Test
    void theConfiguredRangeCannotBeDeactivatedOrDeleted() {
        NumberingRangeDto first = service.createNumberingRange(request("01", "SETP", "111"));

        assertThrows(IllegalStateException.class, () -> service.toggleNumberingRangeStatus(first.id()));
        assertThrows(IllegalStateException.class, () -> service.deleteNumberingRange(first.id()));
        assertEquals(Boolean.TRUE, service.getNumberingRange(first.id()).isActive());
    }

    @Test
    void theConsecutiveNeverGoesBackwardsAndEveryChangeIsAudited() {
        NumberingRangeDto range = service.createNumberingRange(request("01", "SETP", "111"));

        assertEquals(500L, service.updateNumberingRangeCurrent(range.id(), 500L).current());
        assertThrows(IllegalArgumentException.class, () -> service.updateNumberingRangeCurrent(range.id(), 499L));
        assertEquals(500L, service.getNumberingRange(range.id()).current());

        List<String> actions = auditRepository.findByStoreIdAndRangeIdOrderByCreatedAtDesc(storeId, range.id())
                .stream().map(NumberingRangeAudit::getAction).toList();
        assertTrue(actions.containsAll(List.of("CREATE", "SET_DEFAULT", "UPDATE_CURRENT")), actions.toString());
    }

    @Test
    void anotherStoreCannotSeeOrChangeThisStoresRanges() {
        NumberingRangeDto range = service.createNumberingRange(request("01", "SETP", "111"));
        Long other = storeId + 1;
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(other);
        config.setEnvironment(BillingEnvironment.SANDBOX);
        configRepository.save(config);
        TenantContext.setStoreId(other);
        try {
            assertTrue(service.listNumberingRanges().isEmpty());
            assertNull(service.getNumberingRange(range.id()));
        } finally {
            configRepository.deleteById(other);
        }
    }
}
