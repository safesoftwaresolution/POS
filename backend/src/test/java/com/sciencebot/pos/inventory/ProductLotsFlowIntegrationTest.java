package com.sciencebot.pos.inventory;

import com.sciencebot.pos.categories.CategoryDto;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.CreateProductCommand;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.products.ProductLotDto;
import com.sciencebot.pos.products.ProductLotsDto;
import com.sciencebot.pos.reports.ExpiringProductDto;
import com.sciencebot.pos.reports.ReportService;
import com.sciencebot.pos.users.CreateUserCommand;
import com.sciencebot.pos.users.UserFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lotes de vencimiento de punta a punta con beans reales sobre H2: las entradas con fecha crean lotes, las salidas y
 * ventas bajan el stock y el reparto FEFO decide qué lote vence primero, y el reporte y el producto lo reflejan.
 * Cada test usa su propio local.
 */
@SpringBootTest
class ProductLotsFlowIntegrationTest {

    private static final AtomicLong STORE_IDS = new AtomicLong(9000);

    @Autowired private ProductFacade productFacade;
    @Autowired private InventoryFacade inventoryFacade;
    @Autowired private CategoryFacade categoryFacade;
    @Autowired private UserFacade userFacade;
    @Autowired private ReportService reportService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long storeId;
    private String username;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        storeId = STORE_IDS.incrementAndGet();
        TenantContext.setStoreId(storeId);
        username = "lots-" + storeId;
        userFacade.createUser(new CreateUserCommand("Lots Tester", username, username + "@test.local",
                "Password123", "ADMINISTRATOR", storeId));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"))));
        categoryId = categoryFacade.listAll().stream().filter(c -> c.name().equals("Lácteos y Refrigerados")).findFirst()
                .map(CategoryDto::id).orElseThrow();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from inventory_movements where store_id = ?", storeId);
        jdbcTemplate.update("delete from product_lots where store_id = ?", storeId);
        jdbcTemplate.update("delete from products where store_id = ?", storeId);
        jdbcTemplate.update("delete from users where username = ?", username);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void entriesCreateLotsAndSalesConsumeTheLotThatExpiresFirst() {
        Long id = newProduct("LOT-1");
        LocalDate near = LocalDate.now().plusDays(10);
        LocalDate far = LocalDate.now().plusDays(70);

        inventoryFacade.registerMovement(id, "ENTRY", 10, "Cargamento 1", near, "A");
        assertEquals(near, expirationOf(id));

        inventoryFacade.registerMovement(id, "ENTRY", 50, "Cargamento 2", far, "B");
        ProductLotsDto lots = productFacade.listLots(id);
        assertEquals(List.of(10, 50), lots.lots().stream().map(ProductLotDto::remainingQuantity).toList());
        assertEquals(List.of("A", "B"), lots.lots().stream().map(ProductLotDto::lotCode).toList());
        assertEquals(0, lots.undatedQuantity());
        assertEquals(near, expirationOf(id), "el producto vence cuando vence su lote más próximo");
        assertEquals(List.of("A", "B"), report().stream().map(ExpiringProductDto::lotCode).toList());

        // Se venden 10: salen del lote A, que se agota. El aviso y la etiqueta pasan al lote B.
        inventoryFacade.registerMovement(id, "VENTA", 10, "Venta");
        lots = productFacade.listLots(id);
        assertEquals(List.of("B"), lots.lots().stream().map(ProductLotDto::lotCode).toList());
        assertEquals(50, lots.lots().get(0).remainingQuantity());
        assertEquals(far, expirationOf(id));
        List<ExpiringProductDto> report = report();
        assertEquals(List.of("B"), report.stream().map(ExpiringProductDto::lotCode).toList());
        assertEquals(50, report.get(0).lotRemaining());

        // Una salida por vencimiento de 5 unidades también consume primero el lote más próximo (ahora, B).
        inventoryFacade.registerMovement(id, "SALIDA", 5, "Vencimiento");
        assertEquals(45, productFacade.listLots(id).lots().get(0).remainingQuantity());
    }

    @Test
    void anEntryWithAnAbsurdDate_isRejectedAndLeavesNothingBehind() {
        Long id = newProduct("LOT-2");

        assertThrows(IllegalArgumentException.class,
                () -> inventoryFacade.registerMovement(id, "ENTRY", 5, "Cargamento", LocalDate.now().plusYears(40), null));

        assertEquals(0, stockOf(id), "el stock no debe quedar subido si el lote falla");
        assertTrue(productFacade.listLots(id).lots().isEmpty());
        assertEquals(0, countMovements(id));
    }

    @Test
    void anExpirationOnAnExit_isRejected() {
        Long id = newProduct("LOT-3");
        inventoryFacade.registerMovement(id, "ENTRY", 10, "Stock", null, null);

        assertThrows(IllegalArgumentException.class,
                () -> inventoryFacade.registerMovement(id, "SALIDA", 2, "Merma", LocalDate.now().plusDays(5), null));

        assertEquals(10, stockOf(id));
    }

    @Test
    void datingStockThatAlreadyExists_andFixingOrRemovingTheLot() {
        Long id = newProduct("LOT-4");
        inventoryFacade.registerMovement(id, "ENTRY", 20, "Inventario inicial", null, null); // 20 unidades sin fecha
        assertEquals(20, productFacade.listLots(id).undatedQuantity());
        assertNull(expirationOf(id), "sin lotes el producto no vence");

        LocalDate date = LocalDate.now().plusDays(30);
        productFacade.assignLot(id, 20, date, "C");
        assertEquals(date, expirationOf(id));
        assertEquals(0, productFacade.listLots(id).undatedQuantity());
        assertThrows(IllegalArgumentException.class, () -> productFacade.assignLot(id, 1, date, null),
                "ya no quedan unidades sin fecha");

        Long lotId = productFacade.listLots(id).lots().get(0).id();
        LocalDate corrected = LocalDate.now().plusDays(45);
        productFacade.updateLot(id, lotId, corrected, "C2", null);
        assertEquals(corrected, expirationOf(id));
        assertEquals("C2", productFacade.listLots(id).lots().get(0).lotCode());

        productFacade.deleteLot(id, lotId);
        assertNull(expirationOf(id));
        assertEquals(20, productFacade.listLots(id).undatedQuantity(), "quitar el lote no mueve el stock");
        assertEquals(20, stockOf(id));
    }

    /** El escenario que falló: se vendió un lote viejo y luego se quita el lote nuevo; el viejo no debe reaparecer. */
    @Test
    void removingALot_doesNotBringBackAnOldLotThatWasAlreadySold() {
        Long id = newProduct("LOT-7");
        inventoryFacade.registerMovement(id, "ENTRY", 10, "Cargamento 1", LocalDate.now().plusDays(10), null);
        inventoryFacade.registerMovement(id, "ENTRY", 50, "Cargamento 2 (se anotaron 50 y eran 40)", LocalDate.now().plusDays(70), "B");
        inventoryFacade.registerMovement(id, "VENTA", 10, "Venta");               // agota el primer lote
        inventoryFacade.registerMovement(id, "SALIDA", 10, "Conteo físico: sistema 50, contado 40");

        Long lotB = productFacade.listLots(id).lots().get(0).id();
        productFacade.deleteLot(id, lotB);

        ProductLotsDto lots = productFacade.listLots(id);
        assertTrue(lots.lots().isEmpty(), "el lote de la primera entrada ya se vendió: no debe reaparecer");
        assertEquals(40, lots.undatedQuantity());
        assertNull(expirationOf(id));

        // Y ahora sí se puede volver a asignar con la cifra correcta.
        productFacade.assignLot(id, 40, LocalDate.now().plusDays(70), "B");
        ProductLotsDto fixed = productFacade.listLots(id);
        assertEquals(1, fixed.lots().size());
        assertEquals(40, fixed.lots().get(0).remainingQuantity());
        assertEquals(0, fixed.undatedQuantity());
        assertEquals(40, stockOf(id));
    }

    @Test
    void anEntryWithoutDate_doesNotBringBackALotThatWasAlreadySold() {
        Long id = newProduct("LOT-8");
        inventoryFacade.registerMovement(id, "ENTRY", 10, "Cargamento", LocalDate.now().plusDays(5), null);
        inventoryFacade.registerMovement(id, "VENTA", 10, "Venta"); // el lote se vendió completo

        inventoryFacade.registerMovement(id, "ENTRY", 5, "Mercancía sin fecha", null, null);

        ProductLotsDto lots = productFacade.listLots(id);
        assertTrue(lots.lots().isEmpty(), "las 5 unidades nuevas no son del lote vendido");
        assertEquals(5, lots.undatedQuantity());
        assertNull(expirationOf(id));
        assertTrue(report().isEmpty());
    }

    @Test
    void aCustomerReturn_leavesTheReturnedUnitsWithoutDate() {
        Long id = newProduct("LOT-9");
        inventoryFacade.registerMovement(id, "ENTRY", 10, "Cargamento", LocalDate.now().plusDays(5), null);
        inventoryFacade.registerMovement(id, "VENTA", 4, "Venta");

        inventoryFacade.registerMovement(id, "DEVOLUCION_VENTA", 1, "Devolución de un cliente");

        ProductLotsDto lots = productFacade.listLots(id);
        assertEquals(List.of(6), lots.lots().stream().map(ProductLotDto::remainingQuantity).toList());
        assertEquals(1, lots.undatedQuantity());
    }

    @Test
    void changingTheUnitsOfALot_movesThemFromAndToTheUndatedUnits() {
        Long id = newProduct("LOT-10");
        inventoryFacade.registerMovement(id, "ENTRY", 50, "Cargamento (eran 40)", LocalDate.now().plusDays(30), "A");
        Long lotId = productFacade.listLots(id).lots().get(0).id();
        LocalDate date = LocalDate.now().plusDays(30);

        // Bajar de 50 a 40: 10 unidades quedan sin fecha y el stock sigue en 50 hasta que se corrija.
        productFacade.updateLot(id, lotId, date, "A", 40);
        ProductLotsDto lowered = productFacade.listLots(id);
        assertEquals(40, lowered.lots().get(0).remainingQuantity());
        assertEquals(10, lowered.undatedQuantity());
        assertEquals(50, stockOf(id));

        // El conteo corrige el stock: ya no sobra nada y el lote no pierde unidades.
        inventoryFacade.registerMovement(id, "SALIDA", 10, "Conteo físico: sistema 50, contado 40");
        ProductLotsDto counted = productFacade.listLots(id);
        assertEquals(40, counted.lots().get(0).remainingQuantity());
        assertEquals(0, counted.undatedQuantity());

        // Subir las unidades de un lote solo se puede con unidades sin fecha.
        assertThrows(IllegalArgumentException.class, () -> productFacade.updateLot(id, lotId, date, "A", 45));
        inventoryFacade.registerMovement(id, "ENTRY", 5, "Faltaban 5", null, null);
        productFacade.updateLot(id, lotId, date, "A", 45);
        assertEquals(45, productFacade.listLots(id).lots().get(0).remainingQuantity());
        assertEquals(0, productFacade.listLots(id).undatedQuantity());
        assertThrows(IllegalArgumentException.class, () -> productFacade.updateLot(id, lotId, date, "A", 0));
    }

    @Test
    void creatingAProductWithInitialStockAndExpiration_createsOneLot() {
        LocalDate date = LocalDate.now().plusDays(25);

        ProductDto created = productFacade.createProduct(new CreateProductCommand("LOT-5", null, "Queso", categoryId, null,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(2000), 1, 12, null, null, BigDecimal.ZERO, null, date));

        assertEquals(date, created.expirationDate());
        ProductLotsDto lots = productFacade.listLots(created.id());
        assertEquals(1, lots.lots().size());
        assertEquals(12, lots.lots().get(0).quantity());
    }

    @Test
    void lotsOfAnotherStore_areNotVisible() {
        Long id = newProduct("LOT-6");
        inventoryFacade.registerMovement(id, "ENTRY", 5, "Stock", LocalDate.now().plusDays(5), null);

        TenantContext.setStoreId(storeId + 500);

        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> productFacade.listLots(id));
        assertTrue(report().isEmpty());
    }

    private Long newProduct(String code) {
        return productFacade.createProduct(new CreateProductCommand(code, null, "Producto " + code, categoryId, null,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(2000), 1, null, null)).id();
    }

    private LocalDate expirationOf(Long productId) {
        return productFacade.getById(productId).orElseThrow().expirationDate();
    }

    private int stockOf(Long productId) {
        return jdbcTemplate.queryForObject("select quantity_available from products where id = ?", Integer.class, productId);
    }

    private int countMovements(Long productId) {
        return jdbcTemplate.queryForObject("select count(*) from inventory_movements where product_id = ?", Integer.class, productId);
    }

    private List<ExpiringProductDto> report() {
        return reportService.getExpiringProductsReport(365);
    }
}
