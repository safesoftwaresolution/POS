package com.sciencebot.pos.products.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(name = "UK_products_store_internal_code", columnNames = {"store_id", "internal_code"}),
        @UniqueConstraint(name = "UK_products_store_barcode", columnNames = {"store_id", "barcode"})
})
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Local (tenant) dueño del producto. Ningun otro local puede ver ni modificar este registro. */
    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "internal_code", nullable = false, length = 50)
    private String internalCode;

    @Column(length = 50)
    private String barcode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /** Subcategoría propia de la tienda (opcional), debe pertenecer a la misma categoryId. */
    @Column(name = "subcategory_id")
    private Long subcategoryId;

    @Column(name = "purchase_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice = BigDecimal.ZERO;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable = 0;

    @Column(name = "min_stock", nullable = false)
    private int minStock = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "unspsc_code", length = 50)
    private String unspscCode;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
