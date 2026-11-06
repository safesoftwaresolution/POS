package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberAllocator;
import com.sciencebot.pos.billing.internal.adapters.alegra.AlegraNumberingRangeClient;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.adapters.BillingOnboardingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.DianResolutionData;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyProfile;
import com.sciencebot.pos.billing.internal.repositories.BillingCompanyProfileRepository;
import com.sciencebot.pos.billing.internal.repositories.DocumentNumberAllocationRepository;
import com.sciencebot.pos.billing.internal.repositories.NumberingRangeRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.config.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Rangos de numeración locales (Alegra): misma semántica que el puerto, sobre las tablas propias y por local. */
@SpringBootTest
class AlegraNumberingRangeClientTest {

    @Autowired
    private NumberingRangeRepository rangeRepository;
    @Autowired
    private DocumentNumberAllocationRepository allocationRepository;
    @Autowired
    private StoreBillingConfigRepository configRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private BillingCompanyProfileRepository profileRepository;
    private BillingOnboardingProvider onboarding;
    private AlegraNumberingRangeClient client;
    private Long storeId;

    @BeforeEach
    void setUp() {
        profileRepository = mock(BillingCompanyProfileRepository.class);
        onboarding = mock(BillingOnboardingProvider.class);
        client = new AlegraNumberingRangeClient(rangeRepository, allocationRepository, configRepository,
                profileRepository, onboarding, transactionManager);
        storeId = 200_000L + (System.nanoTime() % 1_000_000L);
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(storeId);
        config.setEnvironment(BillingEnvironment.SANDBOX);
        configRepository.save(config);
        TenantContext.setStoreId(storeId);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        allocationRepository.deleteAll(allocationRepository.findAll().stream()
                .filter(a -> a.getStoreId().equals(storeId)).toList());
        rangeRepository.deleteAll(rangeRepository.findAll().stream()
                .filter(r -> r.getStoreId().equals(storeId)).toList());
        configRepository.deleteById(storeId);
    }

    private static CreateNumberingRangeRequest request(String document, String prefix, String resolution, Long from, Long to, Long current) {
        return new CreateNumberingRangeRequest(document, prefix, resolution, current, from, to,
                "2019-01-19", "2030-01-19", "technical-key");
    }

    @Test
    void create_RegistersAnActiveRangeWithTheResolutionData() {
        NumberingRangeDto created = client.createNumberingRange(
                request("01", "SETP", "18760000001", 990_000_000L, 995_000_000L, 990_000_005L));

        assertNotNull(created.id());
        assertEquals("01", created.document());
        assertEquals("SETP", created.prefix());
        assertEquals("18760000001", created.resolutionNumber());
        assertEquals(990_000_000L, created.from());
        assertEquals(995_000_000L, created.to());
        assertEquals(990_000_005L, created.current());
        assertEquals("2030-01-19", created.endDate());
        assertEquals("technical-key", created.technicalKey());
        assertEquals(Boolean.TRUE, created.isActive());
        assertEquals(Boolean.FALSE, created.isExpired());
    }

    @Test
    void create_RejectsMissingBoundsBadDocumentAndNumbersOutsideTheRange() {
        assertThrows(IllegalArgumentException.class, () -> client.createNumberingRange(
                new CreateNumberingRangeRequest("01", "SETP", "1876", 1L)), "Alegra necesita desde y hasta");
        assertThrows(IllegalArgumentException.class, () -> client.createNumberingRange(
                request("21", "SETP", "1876", 1L, 10L, 1L)), "documento fuera de 01/91/92");
        assertThrows(IllegalArgumentException.class, () -> client.createNumberingRange(
                request("01", "SETP", "1876", 10L, 1L, 5L)), "desde mayor que hasta");
        assertThrows(IllegalArgumentException.class, () -> client.createNumberingRange(
                request("01", "SETP", "1876", 1L, 10L, 11L)), "consecutivo fuera del rango");
        assertThrows(IllegalArgumentException.class, () -> client.createNumberingRange(
                request("01", "TOOLONG", "1876", 1L, 10L, 1L)), "prefijo de mas de 4 caracteres");
        assertTrue(client.listNumberingRanges().isEmpty());
    }

    @Test
    void create_RejectsTheSameResolutionAndPrefixTwice() {
        client.createNumberingRange(request("01", "SETP", "1876", 1L, 10L, 1L));

        assertThrows(IllegalStateException.class,
                () -> client.createNumberingRange(request("01", "SETP", "1876", 1L, 10L, 1L)));
    }

    @Test
    void listAndGet_OnlyShowTheCurrentStoresRanges() {
        NumberingRangeDto mine = client.createNumberingRange(request("01", "SETP", "1876", 1L, 10L, 1L));
        TenantContext.setStoreId(storeId + 1);
        configRepository.save(configFor(storeId + 1));
        try {
            assertTrue(client.listNumberingRanges().isEmpty());
            assertNull(client.getNumberingRange(mine.id()), "un rango de otro local figura como inexistente");
        } finally {
            configRepository.deleteById(storeId + 1);
        }
        TenantContext.setStoreId(storeId);

        List<NumberingRangeDto> list = client.listNumberingRanges();
        assertEquals(1, list.size());
        assertEquals(mine.id(), client.getNumberingRange(mine.id()).id());
    }

    @Test
    void toggle_FlipsTheStatus() {
        NumberingRangeDto created = client.createNumberingRange(request("01", "SETP", "1876", 1L, 10L, 1L));

        assertEquals(Boolean.FALSE, client.toggleNumberingRangeStatus(created.id()).isActive());
        assertEquals(Boolean.TRUE, client.toggleNumberingRangeStatus(created.id()).isActive());
    }

    @Test
    void updateCurrent_MovesForwardAndNeverBelowAnAssignedNumber() {
        NumberingRangeDto created = client.createNumberingRange(request("01", "SETP", "1876", 1L, 100L, 1L));
        AlegraNumberAllocator allocator = new AlegraNumberAllocator(rangeRepository, allocationRepository, configRepository, transactionManager);
        allocator.allocate(storeId, BillingEnvironment.SANDBOX, "01", "SALE-1");
        allocator.allocate(storeId, BillingEnvironment.SANDBOX, "01", "SALE-2");

        assertEquals(50L, client.updateNumberingRangeCurrent(created.id(), 50L).current());
        assertThrows(IllegalArgumentException.class, () -> client.updateNumberingRangeCurrent(created.id(), 2L),
                "el numero 2 ya se asigno: volver a usarlo duplicaria el consecutivo");
        assertThrows(IllegalArgumentException.class, () -> client.updateNumberingRangeCurrent(created.id(), 101L),
                "fuera del rango");
        assertEquals(50L, client.getNumberingRange(created.id()).current());
    }

    @Test
    void delete_RemovesAnUnusedRangeAndOnlyMarksAUsedOneAsDeleted() {
        NumberingRangeDto unused = client.createNumberingRange(request("01", "AAAA", "1", 1L, 10L, 1L));
        NumberingRangeDto used = client.createNumberingRange(request("91", "BBBB", "2", 1L, 10L, 1L));
        new AlegraNumberAllocator(rangeRepository, allocationRepository, configRepository, transactionManager)
                .allocate(storeId, BillingEnvironment.SANDBOX, "91", "NC-1");

        client.deleteNumberingRange(unused.id());
        client.deleteNumberingRange(used.id());

        assertNull(client.getNumberingRange(unused.id()));
        assertNull(client.getNumberingRange(used.id()), "un rango eliminado deja de listarse");
        assertTrue(client.listNumberingRanges().isEmpty());
        assertTrue(rangeRepository.findById(unused.id()).isEmpty(), "sin asignaciones se borra de verdad");
        assertTrue(rangeRepository.findById(used.id()).orElseThrow().isDeleted(), "con asignaciones solo se marca");
        assertFalse(rangeRepository.findById(used.id()).orElseThrow().isActive());
    }

    @Test
    void withoutAStoreOrBillingConfig_failsAsNotEnabled() {
        TenantContext.clear();
        assertThrows(BillingNotEnabledException.class, () -> client.listNumberingRanges());

        TenantContext.setStoreId(storeId + 7);
        assertThrows(BillingNotEnabledException.class, () -> client.listNumberingRanges());
    }

    // --- resoluciones de la DIAN del NIT del local (GET /resolutions/{nit}) ---

    private void inProductionWithNit(String nit) {
        StoreBillingConfig config = configRepository.findById(storeId).orElseThrow();
        config.setEnvironment(BillingEnvironment.PRODUCTION);
        configRepository.save(config);
        if (nit != null) {
            BillingCompanyProfile profile = new BillingCompanyProfile();
            profile.setStoreId(storeId);
            profile.setNit(nit);
            when(profileRepository.findById(storeId)).thenReturn(Optional.of(profile));
        }
    }

    @Test
    void queryDianNumberingRanges_InSandboxIsEmptyWithoutAskingTheProvider() {
        assertEquals(List.of(), client.queryDianNumberingRanges());
        verifyNoInteractions(onboarding);
    }

    @Test
    void queryDianNumberingRanges_InProductionListsTheResolutionsOfTheStoreNit() {
        inProductionWithNit("900123456");
        LocalDate start = LocalDate.now().minusMonths(1);
        LocalDate end = LocalDate.now().plusYears(1);
        when(onboarding.listResolutions(BillingEnvironment.PRODUCTION, "900123456")).thenReturn(List.of(
                new DianResolutionData("18764000000001", "FE", 1L, 5000L, start, end, "tk")));

        List<NumberingRangeDto> resolutions = client.queryDianNumberingRanges();

        assertEquals(1, resolutions.size());
        NumberingRangeDto dto = resolutions.get(0);
        assertNull(dto.id(), "todavía no es un rango registrado");
        assertEquals("01", dto.document());
        assertEquals("FE", dto.prefix());
        assertEquals("18764000000001", dto.resolutionNumber());
        assertEquals(1L, dto.from());
        assertEquals(5000L, dto.to());
        assertEquals(start.toString(), dto.startDate());
        assertEquals(end.toString(), dto.endDate());
        assertEquals("tk", dto.technicalKey());
        assertEquals("Factura de Venta", dto.documentName());
        assertEquals(Boolean.FALSE, dto.isExpired());
    }

    @Test
    void queryDianNumberingRanges_InProductionNeedsTheFiscalDataFirst() {
        inProductionWithNit(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.queryDianNumberingRanges());

        assertTrue(ex.getMessage().contains("datos fiscales"), ex.getMessage());
        verify(onboarding, never()).listResolutions(any(), any());
    }

    // --- numeración de facturas utilizable (la usan la guarda de ACTIVE, el cambio de entorno y el asistente) ---

    @Test
    void usableInvoiceRange_IsAnActiveCurrentNotExhaustedInvoiceRangeOfThatEnvironment() {
        assertFalse(client.hasUsableInvoiceRange(storeId, BillingEnvironment.SANDBOX));
        assertEquals(0, client.countInvoiceRanges(storeId, BillingEnvironment.SANDBOX));

        client.createNumberingRange(new CreateNumberingRangeRequest("91", "NC", "SIN-RESOLUCION", 1L, 1L, 10L,
                null, null, null));
        client.createNumberingRange(new CreateNumberingRangeRequest("01", "OLD", "9", 1L, 1L, 10L,
                LocalDate.now().minusYears(3).toString(), LocalDate.now().minusDays(1).toString(), "k"));
        assertFalse(client.hasUsableInvoiceRange(storeId, BillingEnvironment.SANDBOX), "nota crédito y rango vencido");
        assertEquals(1, client.countInvoiceRanges(storeId, BillingEnvironment.SANDBOX));

        NumberingRangeDto current = client.createNumberingRange(new CreateNumberingRangeRequest("01", "FE", "10",
                1L, 1L, 10L, LocalDate.now().minusDays(1).toString(), LocalDate.now().plusYears(1).toString(), "k"));
        assertTrue(client.hasUsableInvoiceRange(storeId, BillingEnvironment.SANDBOX));
        assertFalse(client.hasUsableInvoiceRange(storeId, BillingEnvironment.PRODUCTION), "otro entorno");
        assertFalse(client.hasUsableInvoiceRange(storeId + 1, BillingEnvironment.SANDBOX), "otro local");
        assertEquals(2, client.countInvoiceRanges(storeId, BillingEnvironment.SANDBOX));

        client.toggleNumberingRangeStatus(current.id());
        assertFalse(client.hasUsableInvoiceRange(storeId, BillingEnvironment.SANDBOX), "inactivo");
    }

    @Test
    void usableInvoiceRange_AnExhaustedRangeIsNotUsable() {
        client.createNumberingRange(new CreateNumberingRangeRequest("01", "FE", "10", 10L, 1L, 10L,
                null, null, null));
        assertTrue(client.hasUsableInvoiceRange(storeId, BillingEnvironment.SANDBOX));

        new AlegraNumberAllocator(rangeRepository, allocationRepository, configRepository, transactionManager)
                .allocate(storeId, BillingEnvironment.SANDBOX, "01", "SALE-1");

        assertFalse(client.hasUsableInvoiceRange(storeId, BillingEnvironment.SANDBOX));
    }

    @Test
    void expiredRangeIsFlagged() {
        NumberingRangeDto expired = client.createNumberingRange(new CreateNumberingRangeRequest("01", "OLD", "9", 1L, 1L, 10L,
                LocalDate.now().minusYears(3).toString(), LocalDate.now().minusDays(1).toString(), "k"));
        assertEquals(Boolean.TRUE, expired.isExpired());
    }

    private static StoreBillingConfig configFor(Long id) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(id);
        config.setEnvironment(BillingEnvironment.SANDBOX);
        return config;
    }
}
