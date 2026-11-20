package com.sciencebot.pos.businesses.internal.services;

import com.sciencebot.pos.businesses.BranchDto;
import com.sciencebot.pos.businesses.BranchStatsDto;
import com.sciencebot.pos.businesses.BranchesStatsDto;
import com.sciencebot.pos.businesses.BusinessDto;
import com.sciencebot.pos.businesses.BusinessFacade;
import com.sciencebot.pos.businesses.BusinessStatsDto;
import com.sciencebot.pos.businesses.StatsTotals;
import com.sciencebot.pos.businesses.internal.repositories.BusinessStatsDao;
import com.sciencebot.pos.businesses.internal.repositories.BusinessStatsDao.StoreAmount;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consolidated statistics of the branches of a business for its owner. The branches are taken from the business of
 * the authenticated user, never from the request, and every store of the business is included whatever its status.
 */
@Service
@Transactional(readOnly = true)
public class BusinessStatsService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final BusinessFacade businessFacade;
    private final BusinessStatsDao dao;

    public BusinessStatsService(BusinessFacade businessFacade, BusinessStatsDao dao) {
        this.businessFacade = businessFacade;
        this.dao = dao;
    }

    public BusinessStatsDto overview(String ownerUsername, LocalDate from, LocalDate to) {
        BusinessDto business = requireBusiness(ownerUsername);
        Period period = Period.of(from, to);
        List<Long> storeIds = storeIdsOf(business);

        Map<Long, StoreAmount> sales = byStore(dao.salesByStore(storeIds, period.start(), period.endExclusive()));
        Map<Long, StoreAmount> returns = byStore(dao.returnsByStore(storeIds, period.start(), period.endExclusive()));
        long salesCount = 0;
        long returnsCount = 0;
        BigDecimal salesAmount = BigDecimal.ZERO;
        BigDecimal returnsAmount = BigDecimal.ZERO;
        for (Long id : storeIds) {
            StoreAmount sold = sales.get(id);
            StoreAmount returned = returns.get(id);
            if (sold != null) {
                salesCount += sold.count();
                salesAmount = salesAmount.add(sold.amount());
            }
            if (returned != null) {
                returnsCount += returned.count();
                returnsAmount = returnsAmount.add(returned.amount());
            }
        }
        return new BusinessStatsDto(period.from(), period.to(), storeIds.size(),
                totals(salesCount, salesAmount, returnsCount, returnsAmount),
                dao.paymentMethods(storeIds, period.start(), period.endExclusive()),
                dao.daily(storeIds, period.start(), period.endExclusive()));
    }

    public BranchesStatsDto branches(String ownerUsername, LocalDate from, LocalDate to) {
        BusinessDto business = requireBusiness(ownerUsername);
        Period period = Period.of(from, to);
        List<Long> storeIds = storeIdsOf(business);

        Map<Long, StoreAmount> sales = byStore(dao.salesByStore(storeIds, period.start(), period.endExclusive()));
        Map<Long, StoreAmount> returns = byStore(dao.returnsByStore(storeIds, period.start(), period.endExclusive()));
        Map<Long, Long> lowStock = dao.lowStockByStore(storeIds);

        List<BranchStatsDto> branches = business.branches().stream()
                .map(branch -> branchStats(branch, sales.get(branch.storeId()), returns.get(branch.storeId()),
                        lowStock.getOrDefault(branch.storeId(), 0L)))
                .sorted(Comparator.comparing((BranchStatsDto b) -> b.totals().netAmount()).reversed()
                        .thenComparing(BranchStatsDto::storeId))
                .toList();
        return new BranchesStatsDto(period.from(), period.to(), branches);
    }

    private BranchStatsDto branchStats(BranchDto branch, StoreAmount sold, StoreAmount returned, long lowStock) {
        StatsTotals totals = totals(
                sold == null ? 0 : sold.count(), sold == null ? BigDecimal.ZERO : sold.amount(),
                returned == null ? 0 : returned.count(), returned == null ? BigDecimal.ZERO : returned.amount());
        return new BranchStatsDto(branch.storeId(), branch.name(), branch.status(), branch.main(), totals, lowStock);
    }

    static StatsTotals totals(long salesCount, BigDecimal salesAmount, long returnsCount, BigDecimal returnsAmount) {
        BigDecimal average = salesCount == 0 ? BigDecimal.ZERO.setScale(2)
                : salesAmount.divide(BigDecimal.valueOf(salesCount), 2, RoundingMode.HALF_UP);
        return new StatsTotals(salesCount, salesAmount, average, returnsCount, returnsAmount,
                salesAmount.subtract(returnsAmount));
    }

    private BusinessDto requireBusiness(String ownerUsername) {
        return businessFacade.findByOwnerUsername(ownerUsername)
                .orElseThrow(() -> new AccessDeniedException("No eres dueño de un negocio."));
    }

    private static List<Long> storeIdsOf(BusinessDto business) {
        return business.branches().stream().map(BranchDto::storeId).toList();
    }

    private static Map<Long, StoreAmount> byStore(List<StoreAmount> rows) {
        Map<Long, StoreAmount> map = new HashMap<>();
        rows.forEach(row -> map.put(row.storeId(), row));
        return map;
    }

    /** Inclusive dates turned into a half-open timestamp range. */
    private record Period(LocalDate from, LocalDate to) {
        static Period of(LocalDate from, LocalDate to) {
            LocalDate end = to != null ? to : LocalDate.now();
            LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
            }
            if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
                throw new IllegalArgumentException("El periodo no puede superar " + MAX_DAYS + " días.");
            }
            return new Period(start, end);
        }

        java.time.LocalDateTime start() {
            return from.atStartOfDay();
        }

        java.time.LocalDateTime endExclusive() {
            return to.plusDays(1).atStartOfDay();
        }
    }
}
