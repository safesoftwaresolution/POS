package com.sciencebot.pos.products.internal.services;

import com.sciencebot.pos.products.internal.entities.ProductLot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reparte el stock actual de un producto entre sus lotes, asumiendo que se vende primero lo que vence primero (FEFO).
 *
 * <p>No se guarda cuánto queda de cada lote: el stock del producto es el único contador (su UPDATE atómico no se toca).
 * Con stock {@code S} y lotes ordenados por (vencimiento, id) de cantidades {@code q1..qn}, {@code T = Σq}:
 * lo consumido es {@code max(0, T − S)} y sale primero del lote que vence antes; lo que sobra de {@code S} por encima
 * de {@code T} son unidades sin fecha (entraron sin lote). Es una función pura: sin Spring ni base de datos.
 *
 * <p>La consulta de {@code reports} (ventanas SQL) repite esta misma regla; un test de paridad las compara.
 */
public final class ProductLotAllocator {

    private ProductLotAllocator() {
    }

    /**
     * @param remainingByLotId unidades que quedan de cada lote, en orden de vencimiento (clave = id del lote)
     * @param undated          unidades del stock que no pertenecen a ningún lote
     */
    public record Result(Map<Long, Integer> remainingByLotId, int undated) {

        /** Vencimiento del lote más próximo que todavía tiene unidades, o {@code null} si no hay ninguno. */
        public LocalDate nextExpiration(List<ProductLot> lots) {
            return lots.stream()
                    .filter(lot -> remainingByLotId.getOrDefault(lot.getId(), 0) > 0)
                    .map(ProductLot::getExpirationDate)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
        }
    }

    public static Result allocate(List<ProductLot> lots, int stock) {
        List<ProductLot> ordered = new ArrayList<>(lots);
        ordered.sort(Comparator.comparing(ProductLot::getExpirationDate)
                .thenComparing(ProductLot::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        long total = ordered.stream().mapToLong(ProductLot::getQuantity).sum();
        long available = Math.max(stock, 0);
        long consumed = Math.max(0, total - available);

        Map<Long, Integer> remaining = new LinkedHashMap<>();
        for (ProductLot lot : ordered) {
            long taken = Math.min(lot.getQuantity(), consumed);
            consumed -= taken;
            remaining.put(lot.getId(), (int) (lot.getQuantity() - taken));
        }
        return new Result(remaining, (int) Math.max(0, available - total));
    }
}
