package com.sciencebot.pos.finance.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.finance.*;
import com.sciencebot.pos.finance.internal.entities.FinanceMovement;
import com.sciencebot.pos.finance.internal.entities.FixedCost;
import com.sciencebot.pos.finance.internal.mappers.FinanceMapper;
import com.sciencebot.pos.finance.internal.repositories.FinanceMovementRepository;
import com.sciencebot.pos.finance.internal.repositories.FinanceReadDao;
import com.sciencebot.pos.finance.internal.repositories.FixedCostRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class FinanceServiceImpl implements FinanceFacade {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    /** Un costo fijo mensual se reparte en 30 días, igual que en el resumen de Gastroflow. */
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);

    private final FinanceMovementRepository movementRepository;
    private final FixedCostRepository fixedCostRepository;
    private final FinanceReadDao readDao;
    private final FinanceMapper mapper;

    public FinanceServiceImpl(FinanceMovementRepository movementRepository, FixedCostRepository fixedCostRepository,
                              FinanceReadDao readDao, FinanceMapper mapper) {
        this.movementRepository = movementRepository;
        this.fixedCostRepository = fixedCostRepository;
        this.readDao = readDao;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ resumen

    @Override
    public FinanceSummaryDto getSummary(LocalDate dateFrom, LocalDate dateTo) {
        Long storeId = requireCurrentStoreId();
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(DEFAULT_DAYS - 1L);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final");
        }
        int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new IllegalArgumentException("El período no puede superar " + MAX_DAYS + " días");
        }

        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1L);
        BigDecimal monthlyFixed = readDao.sumActiveFixedCostsMonthly(storeId);

        List<PaymentMethodIncomeDto> incomeByMethod = readDao.findIncomeByPaymentMethod(
                storeId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        Totals current = totals(storeId, from, to, days, monthlyFixed, sum(incomeByMethod));
        Totals previous = totals(storeId, prevFrom, prevTo, days, monthlyFixed,
                sum(readDao.findIncomeByPaymentMethod(storeId, prevFrom.atStartOfDay(), prevTo.plusDays(1).atStartOfDay())));

        return new FinanceSummaryDto(
                from, to, days,
                current.income, readDao.sumMovements(storeId, MovementType.INCOME, from, to),
                current.purchases, current.expenses, current.fixedCosts,
                current.outflow(), current.profit(),
                variationPct(current.income, previous.income),
                variationPct(current.outflow(), previous.outflow()),
                variationPct(current.profit(), previous.profit()),
                readDao.findExpensesByCategory(storeId, from, to),
                incomeByMethod,
                dailySeries(storeId, from, to));
    }

    private Totals totals(Long storeId, LocalDate from, LocalDate to, int days, BigDecimal monthlyFixed, BigDecimal income) {
        BigDecimal purchases = readDao.sumPurchases(storeId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        BigDecimal expenses = readDao.sumMovements(storeId, MovementType.EXPENSE, from, to);
        BigDecimal fixed = monthlyFixed.multiply(BigDecimal.valueOf(days)).divide(DAYS_PER_MONTH, 2, RoundingMode.HALF_UP);
        return new Totals(income, purchases, expenses, fixed);
    }

    private List<DailyFlowDto> dailySeries(Long storeId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> income = readDao.findDailyIncome(storeId, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        Map<LocalDate, BigDecimal> outflow = readDao.findDailyOutflow(storeId, from, to);
        List<DailyFlowDto> series = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            series.add(new DailyFlowDto(day,
                    income.getOrDefault(day, BigDecimal.ZERO),
                    outflow.getOrDefault(day, BigDecimal.ZERO)));
        }
        return series;
    }

    private static BigDecimal sum(List<PaymentMethodIncomeDto> byMethod) {
        return byMethod.stream().map(PaymentMethodIncomeDto::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Variación % de {@code current} frente a {@code previous}; sin base de comparación: 100 si hay valor, 0 si no. */
    public static BigDecimal variationPct(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return current.signum() > 0 ? BigDecimal.valueOf(100).setScale(1) : BigDecimal.ZERO.setScale(1);
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }

    private record Totals(BigDecimal income, BigDecimal purchases, BigDecimal expenses, BigDecimal fixedCosts) {
        BigDecimal outflow() {
            return purchases.add(expenses).add(fixedCosts);
        }

        BigDecimal profit() {
            return income.subtract(outflow());
        }
    }

    // ------------------------------------------------------------------ movimientos manuales

    @Override
    public Page<MovementDto> listMovements(LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        Long storeId = requireCurrentStoreId();
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(DEFAULT_DAYS - 1L);
        return movementRepository
                .findByStoreIdAndMovementDateBetweenOrderByMovementDateDescIdDesc(storeId, from, to, pageable)
                .map(mapper::toDto);
    }

    @Override
    @Transactional
    public MovementDto createMovement(SaveMovementCommand command) {
        FinanceMovement movement = new FinanceMovement();
        movement.setStoreId(requireCurrentStoreId());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        movement.setUsername(auth != null ? auth.getName() : null);
        apply(movement, command);
        return mapper.toDto(movementRepository.save(movement));
    }

    @Override
    @Transactional
    public MovementDto updateMovement(Long id, SaveMovementCommand command) {
        FinanceMovement movement = movementRepository.findByIdAndStoreId(id, requireCurrentStoreId())
                .orElseThrow(() -> new EntityNotFoundException("Movimiento no encontrado con ID: " + id));
        apply(movement, command);
        return mapper.toDto(movement);
    }

    @Override
    @Transactional
    public void deleteMovement(Long id) {
        FinanceMovement movement = movementRepository.findByIdAndStoreId(id, requireCurrentStoreId())
                .orElseThrow(() -> new EntityNotFoundException("Movimiento no encontrado con ID: " + id));
        movementRepository.delete(movement);
    }

    private void apply(FinanceMovement movement, SaveMovementCommand command) {
        if (command.type() == null) {
            throw new IllegalArgumentException("Indica si es un egreso o un ingreso");
        }
        if (command.category() == null || command.category().isBlank()) {
            throw new IllegalArgumentException("La categoría del movimiento es obligatoria");
        }
        BigDecimal amount = requirePositive(command.amount(), "El monto del movimiento");
        LocalDate date = command.movementDate() != null ? command.movementDate() : LocalDate.now();
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha del movimiento no puede ser futura");
        }
        movement.setType(command.type());
        movement.setCategory(command.category().trim());
        movement.setDescription(blankToNull(command.description()));
        movement.setAmount(amount);
        movement.setPaymentMethod(command.paymentMethod() == null || command.paymentMethod().isBlank()
                ? "CASH" : command.paymentMethod().trim().toUpperCase());
        movement.setMovementDate(date);
    }

    // ------------------------------------------------------------------ costos fijos

    @Override
    public List<FixedCostDto> listFixedCosts() {
        return fixedCostRepository.findByStoreIdOrderByNameAsc(requireCurrentStoreId()).stream()
                .map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public FixedCostDto createFixedCost(SaveFixedCostCommand command) {
        Long storeId = requireCurrentStoreId();
        String name = requireName(command);
        if (fixedCostRepository.existsByStoreIdAndNameIgnoreCase(storeId, name)) {
            throw new IllegalArgumentException("Ya existe un costo fijo llamado: " + name);
        }
        FixedCost cost = new FixedCost();
        cost.setStoreId(storeId);
        cost.setName(name);
        cost.setMonthlyAmount(requirePositive(command.monthlyAmount(), "El monto mensual"));
        cost.setDueDay(requireDueDay(command.dueDay()));
        cost.setActive(command.active() == null || command.active());
        return mapper.toDto(fixedCostRepository.save(cost));
    }

    @Override
    @Transactional
    public FixedCostDto updateFixedCost(Long id, SaveFixedCostCommand command) {
        Long storeId = requireCurrentStoreId();
        FixedCost cost = fixedCostRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new EntityNotFoundException("Costo fijo no encontrado con ID: " + id));
        String name = requireName(command);
        if (fixedCostRepository.existsByStoreIdAndNameIgnoreCaseAndIdNot(storeId, name, id)) {
            throw new IllegalArgumentException("Ya existe un costo fijo llamado: " + name);
        }
        cost.setName(name);
        cost.setMonthlyAmount(requirePositive(command.monthlyAmount(), "El monto mensual"));
        cost.setDueDay(requireDueDay(command.dueDay()));
        if (command.active() != null) {
            cost.setActive(command.active());
        }
        return mapper.toDto(cost);
    }

    @Override
    @Transactional
    public void deleteFixedCost(Long id) {
        FixedCost cost = fixedCostRepository.findByIdAndStoreId(id, requireCurrentStoreId())
                .orElseThrow(() -> new EntityNotFoundException("Costo fijo no encontrado con ID: " + id));
        fixedCostRepository.delete(cost);
    }

    // ------------------------------------------------------------------ helpers

    private static String requireName(SaveFixedCostCommand command) {
        if (command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("El nombre del costo fijo es obligatorio");
        }
        return command.name().trim();
    }

    /** El día de cobro es opcional; si viene, debe estar entre 1 y 31 (en un mes más corto vale el último día). */
    private static Integer requireDueDay(Integer dueDay) {
        if (dueDay != null && (dueDay < 1 || dueDay > 31)) {
            throw new IllegalArgumentException("El día de cobro debe estar entre 1 y 31");
        }
        return dueDay;
    }

    private static BigDecimal requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " debe ser mayor a 0");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Long requireCurrentStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
