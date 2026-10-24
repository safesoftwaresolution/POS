package com.sciencebot.pos.products;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.internal.entities.Product;
import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ventas concurrentes del mismo producto: el stock final tiene que ser exacto (sin lost-update) y nunca
 * negativo (sin sobreventa). Se ejecuta fuera de una transaccion de test para que cada hilo tenga la suya,
 * y cada hilo lee el producto ANTES de ajustar el stock, como hace el flujo real de ventas (ese era el caso
 * que un SELECT ... FOR UPDATE + modificar la entidad no cubria).
 */
@SpringBootTest
class StockConcurrencyTest {

    @Autowired
    private ProductFacade productFacade;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long productId;

    @BeforeEach
    void createProduct() {
        Product product = new Product();
        product.setStoreId(1L);
        product.setInternalCode("CONC-" + System.nanoTime());
        product.setName("Producto concurrente");
        product.setCategoryId(1L);
        product.setPurchasePrice(BigDecimal.ONE);
        product.setSalePrice(BigDecimal.TEN);
        product.setActive(true);
        product.setQuantityAvailable(50);
        productId = productRepository.save(product).getId();
    }

    @AfterEach
    void cleanUp() {
        productRepository.deleteById(productId);
        TenantContext.clear();
    }

    @Test
    void parallelSales_neverLoseAnUpdate() throws Exception {
        List<Boolean> results = runParallel(40, -1);

        assertEquals(40, results.stream().filter(ok -> ok).count());
        assertEquals(10, productRepository.findStockById(productId));
    }

    @Test
    void parallelSales_neverOversell() throws Exception {
        List<Boolean> results = runParallel(80, -1); // hay stock para 50

        assertEquals(50, results.stream().filter(ok -> ok).count());
        assertEquals(30, results.stream().filter(ok -> !ok).count());
        assertEquals(0, productRepository.findStockById(productId));
    }

    @Test
    void adjustStock_returnsTheResultingStockAndRejectsNegative() {
        TenantContext.setStoreId(1L);

        assertEquals(45, productFacade.adjustStock(productId, -5));
        assertEquals(55, productFacade.adjustStock(productId, 10));
        assertThrows(IllegalArgumentException.class, () -> productFacade.adjustStock(productId, -56));
        assertEquals(55, productRepository.findStockById(productId));
    }

    @Test
    void adjustStock_ofAnotherStoreIsTreatedAsNotFound() {
        TenantContext.setStoreId(2L);

        assertThrows(EntityNotFoundException.class, () -> productFacade.adjustStock(productId, -1));
        assertEquals(50, productRepository.findStockById(productId));
    }

    /** Lanza {@code threads} ventas a la vez; true = se aplico, false = rechazada por stock insuficiente. */
    private List<Boolean> runParallel(int threads, int delta) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger unexpected = new AtomicInteger();
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> sale = () -> {
                TenantContext.setStoreId(1L);
                try {
                    start.await();
                    try {
                        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                            productRepository.findById(productId).orElseThrow(); // lectura previa, como en la venta real
                            productFacade.adjustStock(productId, delta);
                        });
                        return true;
                    } catch (IllegalArgumentException insufficient) {
                        return false; // la excepcion hace rollback de la transaccion de ESTA venta, como en produccion
                    }
                } catch (RuntimeException ex) {
                    unexpected.incrementAndGet();
                    throw ex;
                } finally {
                    TenantContext.clear();
                }
            };
            futures.add(pool.submit(sale));
        }
        start.countDown();
        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        assertEquals(0, unexpected.get());
        return results;
    }
}
