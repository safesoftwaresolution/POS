package com.sciencebot.pos.sales.internal.repositories;

import com.sciencebot.pos.sales.internal.entities.SaleReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {

    List<SaleReturn> findAllBySaleIdOrderByCreatedAtDesc(Long saleId);

    /** Suma ya devuelta de una linea de venta especifica, sumando todas sus devoluciones previas. */
    @Query("SELECT COALESCE(SUM(ri.quantity), 0) FROM SaleReturnItem ri WHERE ri.saleItemId = :saleItemId")
    int sumReturnedQuantityBySaleItemId(@Param("saleItemId") Long saleItemId);
}
