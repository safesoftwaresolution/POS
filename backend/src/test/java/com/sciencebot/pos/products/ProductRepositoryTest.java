package com.sciencebot.pos.products;

import com.sciencebot.pos.products.internal.entities.Product;
import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import com.sciencebot.pos.products.internal.repositories.ProductSpecifications;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void testSearchProducts() {
        for (int i = 0; i < 15; i++) {
            Product product = new Product();
            product.setStoreId(1L);
            product.setInternalCode("PROD-100" + i);
            product.setName("Test Product " + i);
            product.setCategoryId(1L);
            product.setPurchasePrice(BigDecimal.TEN);
            product.setSalePrice(BigDecimal.TEN);
            product.setActive(true);
            productRepository.save(product);
        }

        // Test with search as null, page size 10, offset 0 (forces count query since total > page size)
        Specification<Product> specNull = Specification.unrestricted();
        Page<Product> resultNull = productRepository.findAll(specNull, PageRequest.of(0, 10));
        assertEquals(15, resultNull.getTotalElements());
        assertEquals(10, resultNull.getContent().size());

        // Test with search matching name
        Specification<Product> specSearch = ProductSpecifications.hasName("test")
                .or(ProductSpecifications.hasInternalCode("test"))
                .or(ProductSpecifications.hasBarcode("test"));
        Page<Product> resultSearch = productRepository.findAll(specSearch, PageRequest.of(0, 10));
        assertEquals(15, resultSearch.getTotalElements());
    }
}
