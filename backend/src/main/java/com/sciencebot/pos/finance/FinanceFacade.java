package com.sciencebot.pos.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface FinanceFacade {

    /** Estado financiero (base caja). Sin fechas usa los últimos 30 días; el máximo es 366. */
    FinanceSummaryDto getSummary(LocalDate dateFrom, LocalDate dateTo);

    /** Movimientos manuales (egresos e ingresos) del período, el más reciente primero. */
    Page<MovementDto> listMovements(LocalDate dateFrom, LocalDate dateTo, Pageable pageable);

    MovementDto createMovement(SaveMovementCommand command);

    MovementDto updateMovement(Long id, SaveMovementCommand command);

    void deleteMovement(Long id);

    List<FixedCostDto> listFixedCosts();

    FixedCostDto createFixedCost(SaveFixedCostCommand command);

    FixedCostDto updateFixedCost(Long id, SaveFixedCostCommand command);

    void deleteFixedCost(Long id);
}
