package com.sciencebot.pos.products.internal.services;

import com.sciencebot.pos.products.internal.entities.ProductLot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regla de reparto FEFO: el stock consumido sale del lote que vence primero; lo que sobra del stock es "sin fecha". */
class ProductLotAllocatorTest {

    private static final LocalDate NOV = LocalDate.of(2026, 11, 10);
    private static final LocalDate JAN = LocalDate.of(2027, 1, 15);
    private static final LocalDate MAR = LocalDate.of(2027, 3, 1);

    @Test
    void nothingSold_everyLotIsIntact() {
        List<ProductLot> lots = List.of(lot(1, 10, NOV), lot(2, 50, JAN));

        ProductLotAllocator.Result result = ProductLotAllocator.allocate(lots, 60);

        assertEquals(Map.of(1L, 10, 2L, 50), result.remainingByLotId());
        assertEquals(0, result.undated());
        assertEquals(NOV, result.nextExpiration(lots));
    }

    @Test
    void sales_comeOutOfTheLotThatExpiresFirst() {
        List<ProductLot> lots = List.of(lot(1, 10, NOV), lot(2, 50, JAN));

        ProductLotAllocator.Result result = ProductLotAllocator.allocate(lots, 56); // se vendieron 4

        assertEquals(6, result.remainingByLotId().get(1L));
        assertEquals(50, result.remainingByLotId().get(2L));
        assertEquals(NOV, result.nextExpiration(lots));
    }

    @Test
    void salesThatExhaustTheFirstLot_moveTheNextExpirationToTheNextLot() {
        List<ProductLot> lots = List.of(lot(1, 10, NOV), lot(2, 50, JAN));

        ProductLotAllocator.Result result = ProductLotAllocator.allocate(lots, 50); // se vendieron 10

        assertEquals(0, result.remainingByLotId().get(1L));
        assertEquals(50, result.remainingByLotId().get(2L));
        assertEquals(JAN, result.nextExpiration(lots));
    }

    @Test
    void salesThatCrossSeveralLots_consumeThemInOrder() {
        List<ProductLot> lots = List.of(lot(1, 10, NOV), lot(2, 20, JAN), lot(3, 30, MAR));

        ProductLotAllocator.Result result = ProductLotAllocator.allocate(lots, 25); // 35 vendidas: agota 1 y 2 y toca el 3

        assertEquals(Map.of(1L, 0, 2L, 0, 3L, 25), result.remainingByLotId());
        assertEquals(MAR, result.nextExpiration(lots));
    }

    @Test
    void emptyStock_leavesNoLotWithUnits() {
        List<ProductLot> lots = List.of(lot(1, 10, NOV), lot(2, 50, JAN));

        ProductLotAllocator.Result result = ProductLotAllocator.allocate(lots, 0);

        assertEquals(Map.of(1L, 0, 2L, 0), result.remainingByLotId());
        assertEquals(0, result.undated());
        assertNull(result.nextExpiration(lots));
    }

    @Test
    void stockAboveTheLots_isUndatedAndDoesNotTouchTheLots() {
        List<ProductLot> lots = List.of(lot(1, 10, NOV));

        ProductLotAllocator.Result result = ProductLotAllocator.allocate(lots, 17);

        assertEquals(Map.of(1L, 10), result.remainingByLotId());
        assertEquals(7, result.undated());
    }

    @Test
    void withoutLots_everyUnitIsUndatedAndThereIsNoExpiration() {
        ProductLotAllocator.Result result = ProductLotAllocator.allocate(List.of(), 40);

        assertTrue(result.remainingByLotId().isEmpty());
        assertEquals(40, result.undated());
        assertNull(result.nextExpiration(List.of()));
    }

    @Test
    void negativeStock_isTreatedAsZero() {
        ProductLotAllocator.Result result = ProductLotAllocator.allocate(List.of(lot(1, 10, NOV)), -3);

        assertEquals(0, result.remainingByLotId().get(1L));
        assertEquals(0, result.undated());
    }

    @Test
    void sameExpiration_isResolvedByLotId() {
        // Dos lotes de 3 el mismo día y 4 unidades: se consumieron 2 y salen del lote de menor id.
        List<ProductLot> lots = List.of(lot(8, 3, NOV), lot(5, 3, NOV));

        ProductLotAllocator.Result result = ProductLotAllocator.allocate(lots, 4);

        assertEquals(1, result.remainingByLotId().get(5L));
        assertEquals(3, result.remainingByLotId().get(8L));
    }

    @Test
    void lotsGivenOutOfOrder_areAllocatedByExpirationNotByPosition() {
        List<ProductLot> lots = List.of(lot(2, 50, JAN), lot(1, 10, NOV));

        ProductLotAllocator.Result result = ProductLotAllocator.allocate(lots, 50);

        assertEquals(0, result.remainingByLotId().get(1L));
        assertEquals(50, result.remainingByLotId().get(2L));
    }

    @Test
    void aCustomerReturn_unconsumesTheLotThatExpiresFirst() {
        List<ProductLot> lots = List.of(lot(1, 10, NOV), lot(2, 50, JAN));

        // Se vendieron 10 (stock 50) y el cliente devuelve 3: el stock sube a 53 y esas unidades vuelven al lote de nov.
        ProductLotAllocator.Result result = ProductLotAllocator.allocate(lots, 53);

        assertEquals(3, result.remainingByLotId().get(1L));
        assertEquals(50, result.remainingByLotId().get(2L));
    }

    private static ProductLot lot(long id, int quantity, LocalDate expiration) {
        ProductLot lot = new ProductLot();
        lot.setId(id);
        lot.setQuantity(quantity);
        lot.setExpirationDate(expiration);
        return lot;
    }
}
