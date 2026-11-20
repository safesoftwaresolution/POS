package com.sciencebot.pos.businesses;

import com.sciencebot.pos.businesses.internal.repositories.BusinessStatsDao;
import com.sciencebot.pos.businesses.internal.repositories.BusinessStatsDao.StoreAmount;
import com.sciencebot.pos.businesses.internal.services.BusinessStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BusinessStatsServiceTest {

    @Mock private BusinessFacade businessFacade;
    @Mock private BusinessStatsDao dao;

    private BusinessStatsService service;

    private static final LocalDate FROM = LocalDate.of(2026, 3, 10);
    private static final LocalDate TO = LocalDate.of(2026, 3, 12);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new BusinessStatsService(businessFacade, dao);
        BusinessDto business = new BusinessDto(7L, "Mi Negocio", 5L, 1L, BillingMode.SEPARATE_TAX_ID,
                CatalogMode.INDEPENDENT, 3,
                List.of(new BranchDto(1L, "Principal", "ACTIVE", true), new BranchDto(2L, "Norte", "ACTIVE", false),
                        new BranchDto(3L, "Sur", "PENDING_VERIFICATION", false)), null);
        when(businessFacade.findByOwnerUsername("dueno")).thenReturn(Optional.of(business));
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void overview_addsUpEveryBranch_andPassesTheBusinessStoresAndAHalfOpenPeriod() {
        when(dao.salesByStore(anyCollection(), any(), any())).thenReturn(List.of(
                new StoreAmount(1L, 3, d("180.00")), new StoreAmount(2L, 1, d("200.00"))));
        when(dao.returnsByStore(anyCollection(), any(), any())).thenReturn(List.of(new StoreAmount(1L, 1, d("20.00"))));

        BusinessStatsDto stats = service.overview("dueno", FROM, TO);

        assertEquals(3, stats.branchCount());
        assertEquals(4, stats.totals().salesCount());
        assertEquals(0, d("380.00").compareTo(stats.totals().salesAmount()));
        assertEquals(0, d("95.00").compareTo(stats.totals().averageTicket()));
        assertEquals(0, d("20.00").compareTo(stats.totals().returnsAmount()));
        assertEquals(0, d("360.00").compareTo(stats.totals().netAmount()));

        ArgumentCaptor<java.util.Collection<Long>> ids = ArgumentCaptor.forClass(java.util.Collection.class);
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dao).salesByStore(ids.capture(), from.capture(), to.capture());
        assertEquals(List.of(1L, 2L, 3L), List.copyOf(ids.getValue()), "the stores come from the owner's business");
        assertEquals(LocalDate.of(2026, 3, 10).atStartOfDay(), from.getValue());
        assertEquals(LocalDate.of(2026, 3, 13).atStartOfDay(), to.getValue(), "the last day is included");
    }

    @Test
    void overview_withoutSales_hasZeroTotalsAndAZeroAverage() {
        when(dao.salesByStore(anyCollection(), any(), any())).thenReturn(List.of());
        when(dao.returnsByStore(anyCollection(), any(), any())).thenReturn(List.of());

        StatsTotals totals = service.overview("dueno", FROM, TO).totals();

        assertEquals(0, totals.salesCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(totals.averageTicket()));
        assertEquals(0, BigDecimal.ZERO.compareTo(totals.netAmount()));
    }

    @Test
    void branches_areRankedByNetAmount_andIncludeBranchesWithoutSales() {
        when(dao.salesByStore(anyCollection(), any(), any())).thenReturn(List.of(
                new StoreAmount(1L, 3, d("180.00")), new StoreAmount(2L, 1, d("200.00"))));
        when(dao.returnsByStore(anyCollection(), any(), any())).thenReturn(List.of(new StoreAmount(2L, 1, d("50.00"))));
        when(dao.lowStockByStore(anyCollection())).thenReturn(Map.of(1L, 2L));

        BranchesStatsDto stats = service.branches("dueno", FROM, TO);

        assertEquals(List.of(1L, 2L, 3L), stats.branches().stream().map(BranchStatsDto::storeId).toList(),
                "net: 180 (Principal), 150 (Norte), 0 (Sur)");
        BranchStatsDto main = stats.branches().get(0);
        assertTrue(main.main());
        assertEquals(2, main.lowStockProducts());
        assertEquals(0, d("150.00").compareTo(stats.branches().get(1).totals().netAmount()));
        BranchStatsDto pending = stats.branches().get(2);
        assertEquals("PENDING_VERIFICATION", pending.status());
        assertEquals(0, pending.totals().salesCount());
        assertEquals(0, pending.lowStockProducts());
    }

    @Test
    void aUserWhoOwnsNoBusiness_isDenied_andNothingIsQueried() {
        when(businessFacade.findByOwnerUsername("otro")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.overview("otro", FROM, TO));
        assertThrows(AccessDeniedException.class, () -> service.branches("otro", FROM, TO));
        verifyNoInteractions(dao);
    }

    @Test
    void thePeriodDefaultsToTheLast30DaysAndIsValidated() {
        when(dao.salesByStore(anyCollection(), any(), any())).thenReturn(List.of());
        when(dao.returnsByStore(anyCollection(), any(), any())).thenReturn(List.of());

        BusinessStatsDto defaults = service.overview("dueno", null, null);
        assertEquals(LocalDate.now(), defaults.to());
        assertEquals(LocalDate.now().minusDays(29), defaults.from());

        assertThrows(IllegalArgumentException.class, () -> service.overview("dueno", TO, FROM));
        assertThrows(IllegalArgumentException.class,
                () -> service.overview("dueno", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 3, 1)));
        assertDoesNotThrow(() -> service.overview("dueno", LocalDate.of(2025, 3, 12), LocalDate.of(2026, 3, 12)));
    }
}
