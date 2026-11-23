package com.sciencebot.pos.finance;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.finance.internal.entities.FinanceMovement;
import com.sciencebot.pos.finance.internal.entities.FixedCost;
import com.sciencebot.pos.finance.internal.mappers.FinanceMapper;
import com.sciencebot.pos.finance.internal.repositories.FinanceMovementRepository;
import com.sciencebot.pos.finance.internal.repositories.FinanceReadDao;
import com.sciencebot.pos.finance.internal.repositories.FixedCostRepository;
import com.sciencebot.pos.finance.internal.services.FinanceServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceServiceImplTest {

    private static final long STORE = 7L;

    @Mock private FinanceMovementRepository movementRepository;
    @Mock private FixedCostRepository fixedCostRepository;
    @Mock private FinanceReadDao readDao;

    private FinanceServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setStoreId(STORE);
        service = new FinanceServiceImpl(movementRepository, fixedCostRepository, readDao, new FinanceMapper());
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void createMovement_defaultsToTodayAndCash_forAnExpenseAndForAnIncome() {
        when(movementRepository.save(any(FinanceMovement.class))).thenAnswer(i -> i.getArgument(0));

        MovementDto expense = service.createMovement(
                new SaveMovementCommand(MovementType.EXPENSE, " Servicios ", " ", new BigDecimal("180000"), null, null));
        MovementDto income = service.createMovement(
                new SaveMovementCommand(MovementType.INCOME, "Otros ingresos", null, new BigDecimal("50000"), "nequi", null));

        assertEquals(MovementType.EXPENSE, expense.type());
        assertEquals("Servicios", expense.category());
        assertNull(expense.description());
        assertEquals("CASH", expense.paymentMethod());
        assertEquals(LocalDate.now(), expense.movementDate());
        assertEquals(0, new BigDecimal("180000").compareTo(expense.amount()));
        assertEquals(MovementType.INCOME, income.type());
        assertEquals("NEQUI", income.paymentMethod());
    }

    @Test
    void createMovement_rejectsMissingTypeFutureDateZeroAmountAndBlankCategory() {
        var noType = new SaveMovementCommand(null, "Servicios", null, BigDecimal.TEN, "CASH", null);
        var future = new SaveMovementCommand(MovementType.EXPENSE, "Servicios", null, BigDecimal.TEN, "CASH", LocalDate.now().plusDays(1));
        var zero = new SaveMovementCommand(MovementType.EXPENSE, "Servicios", null, BigDecimal.ZERO, "CASH", null);
        var blank = new SaveMovementCommand(MovementType.INCOME, "  ", null, BigDecimal.TEN, "CASH", null);

        assertThrows(IllegalArgumentException.class, () -> service.createMovement(noType));
        assertThrows(IllegalArgumentException.class, () -> service.createMovement(future));
        assertThrows(IllegalArgumentException.class, () -> service.createMovement(zero));
        assertThrows(IllegalArgumentException.class, () -> service.createMovement(blank));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void updateMovement_canSwitchTheType() {
        FinanceMovement existing = new FinanceMovement();
        existing.setStoreId(STORE);
        existing.setType(MovementType.EXPENSE);
        existing.setCategory("Servicios");
        existing.setAmount(BigDecimal.TEN);
        existing.setMovementDate(LocalDate.now());
        when(movementRepository.findByIdAndStoreId(4L, STORE)).thenReturn(Optional.of(existing));

        MovementDto updated = service.updateMovement(4L,
                new SaveMovementCommand(MovementType.INCOME, "Otros ingresos", null, new BigDecimal("99"), "CASH", null));

        assertEquals(MovementType.INCOME, updated.type());
        assertEquals(0, new BigDecimal("99").compareTo(updated.amount()));
    }

    @Test
    void updateAndDeleteMovement_ofAnotherStore_areNotFound() {
        when(movementRepository.findByIdAndStoreId(5L, STORE)).thenReturn(Optional.empty());

        var command = new SaveMovementCommand(MovementType.EXPENSE, "Servicios", null, BigDecimal.TEN, "CASH", null);
        assertThrows(EntityNotFoundException.class, () -> service.updateMovement(5L, command));
        assertThrows(EntityNotFoundException.class, () -> service.deleteMovement(5L));
        verify(movementRepository, never()).delete(any());
    }

    @Test
    void createFixedCost_rejectsDuplicateNameInTheSameStore() {
        when(fixedCostRepository.existsByStoreIdAndNameIgnoreCase(STORE, "Arriendo")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> service.createFixedCost(new SaveFixedCostCommand("Arriendo", new BigDecimal("1500000"), 5, null)));
        verify(fixedCostRepository, never()).save(any());
    }

    @Test
    void createFixedCost_isActiveByDefault_keepsTheDueDay_andUpdateKeepsActiveWhenOmitted() {
        when(fixedCostRepository.save(any(FixedCost.class))).thenAnswer(i -> i.getArgument(0));

        FixedCostDto created = service.createFixedCost(new SaveFixedCostCommand("Arriendo", new BigDecimal("1500000"), 5, null));
        assertTrue(created.active());
        assertEquals(5, created.dueDay());

        FixedCost existing = new FixedCost();
        existing.setStoreId(STORE);
        existing.setName("Arriendo");
        existing.setMonthlyAmount(BigDecimal.TEN);
        existing.setDueDay(5);
        existing.setActive(false);
        when(fixedCostRepository.findByIdAndStoreId(3L, STORE)).thenReturn(Optional.of(existing));

        FixedCostDto updated = service.updateFixedCost(3L, new SaveFixedCostCommand("Arriendo", new BigDecimal("2000000"), null, null));
        assertFalse(updated.active());
        assertNull(updated.dueDay(), "sin día en el comando, el costo queda sin día de cobro");
        assertEquals(0, new BigDecimal("2000000").compareTo(updated.monthlyAmount()));
    }

    @Test
    void fixedCost_rejectsADueDayOutsideOneToThirtyOne() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createFixedCost(new SaveFixedCostCommand("Arriendo", BigDecimal.TEN, 0, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.createFixedCost(new SaveFixedCostCommand("Arriendo", BigDecimal.TEN, 32, null)));
        verify(fixedCostRepository, never()).save(any());
    }

    @Test
    void getSummary_rejectsInvertedAndTooLongRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getSummary(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.getSummary(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1)));
    }

    @Test
    void operations_withoutAStoreFail() {
        TenantContext.clear();
        assertThrows(IllegalStateException.class, () -> service.listFixedCosts());
    }

    @Test
    void variationPct_handlesAZeroBase() {
        assertEquals(0, new BigDecimal("100.0").compareTo(FinanceServiceImpl.variationPct(BigDecimal.TEN, BigDecimal.ZERO)));
        assertEquals(0, BigDecimal.ZERO.compareTo(FinanceServiceImpl.variationPct(BigDecimal.ZERO, BigDecimal.ZERO)));
        assertEquals(0, new BigDecimal("-50.0").compareTo(FinanceServiceImpl.variationPct(new BigDecimal("50"), new BigDecimal("100"))));
    }
}
