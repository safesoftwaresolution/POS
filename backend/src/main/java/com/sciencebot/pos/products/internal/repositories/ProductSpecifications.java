package com.sciencebot.pos.products.internal.repositories;

import org.springframework.data.jpa.domain.Specification;

import com.sciencebot.pos.products.internal.entities.Product;

public class ProductSpecifications {

    public static Specification<Product> hasName(String name) {

        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("name")),
                        "%" + name.toLowerCase() + "%"
                );
    } 

    public static Specification<Product> hasInternalCode(String internalCode) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("internalCode")),
                        "%" + internalCode.toLowerCase() + "%"
                );
    }

    public static Specification<Product> hasBarcode(String barcode) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("barcode")),
                        "%" + barcode.toLowerCase() + "%"
                );
    }

    public static Specification<Product> hasCategoryId(Long categoryId) {
        return (root, query, cb) ->
                cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<Product> hasActive(Boolean active) {
        return (root, query, cb) ->
                cb.equal(root.get("active"), active);
    }

    public static Specification<Product> hasStoreId(Long storeId) {
        return (root, query, cb) ->
                cb.equal(root.get("storeId"), storeId);
    }

}
