package com.sciencebot.pos.sales.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_return_items")
@Getter
@Setter
public class SaleReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_return_id", nullable = false)
    private SaleReturn saleReturn;

    /** Referencia la linea exacta de la venta original que se esta devolviendo. */
    @Column(name = "sale_item_id", nullable = false)
    private Long saleItemId;

    /** Copia de sale_items.product_id al momento de la devolucion (evita un join extra). */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;
}
