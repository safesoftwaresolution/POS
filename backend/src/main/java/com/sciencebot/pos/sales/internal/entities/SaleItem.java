package com.sciencebot.pos.sales.internal.entities;

import com.sciencebot.pos.sales.SaleItemType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
@Getter
@Setter
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    /** Producto vendido; null si la linea es un servicio (item_type = SERVICE). */
    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    private SaleItemType itemType = SaleItemType.PRODUCT;

    /** Servicio vendido; null si la linea es un producto. */
    @Column(name = "service_id")
    private Long serviceId;

    /** Copia del nombre/medida/UNSPSC facturados: cambiar o desactivar el servicio no altera la venta emitida. */
    @Column(name = "item_name", length = 150)
    private String itemName;

    @Column(name = "unit_measure_code", length = 10)
    private String unitMeasureCode;

    @Column(name = "unspsc_code", length = 50)
    private String unspscCode;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;
}
