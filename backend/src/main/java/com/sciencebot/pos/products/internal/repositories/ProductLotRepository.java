package com.sciencebot.pos.products.internal.repositories;

import com.sciencebot.pos.products.internal.entities.ProductLot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Todas las consultas filtran por local (TenantScopedRepositoriesTest lo exige): un lote nunca cruza de tienda. */
public interface ProductLotRepository extends JpaRepository<ProductLot, Long> {

    /** Lotes de un producto, del que vence primero al último (el desempate por id fija el orden del reparto FEFO). */
    List<ProductLot> findByStoreIdAndProductIdOrderByExpirationDateAscIdAsc(Long storeId, Long productId);

    /** Lotes de varios productos en una sola consulta (listados de productos). */
    List<ProductLot> findByStoreIdAndProductIdInOrderByExpirationDateAscIdAsc(Long storeId, Collection<Long> productIds);
}
