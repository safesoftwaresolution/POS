package com.sciencebot.pos.inventory;

import com.sciencebot.pos.categories.CategoryDto;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.categories.SubcategoryFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.CreateProductCommand;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.users.CreateUserCommand;
import com.sciencebot.pos.users.UserFacade;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Importación masiva contra H2 con beans reales y transacciones reales (sin @Transactional de test): lo que se
 * prueba es justamente qué queda escrito en la BD, incluido el "todo o nada". Cada test usa su propio local.
 */
@SpringBootTest
class ProductImportIntegrationTest {

    private static final AtomicLong STORE_IDS = new AtomicLong(7000);
    private static final String[] HEADER = {
            "Código interno *", "Nombre *", "Categoría *", "Subcategoría", "Código de barras", "Precio de compra",
            "Precio de venta *", "IVA %", "Stock", "Stock mínimo", "Descripción", "Código UNSPSC"};

    @Autowired
    private ProductImportFacade productImportFacade;
    @Autowired
    private ProductFacade productFacade;
    @Autowired
    private InventoryFacade inventoryFacade;
    @Autowired
    private CategoryFacade categoryFacade;
    @Autowired
    private SubcategoryFacade subcategoryFacade;
    @Autowired
    private UserFacade userFacade;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long storeId;
    private String username;
    private CategoryDto drinks;
    private CategoryDto groceries;

    @BeforeEach
    void setUp() {
        storeId = STORE_IDS.incrementAndGet();
        TenantContext.setStoreId(storeId);
        // El kardex guarda quién hizo cada movimiento: sin un usuario real el mapper de movimientos no lo
        // encuentra y deja la transacción marcada para rollback (DatabaseSeeder solo siembra 'admin' en dev).
        username = "import-" + storeId;
        userFacade.createUser(new CreateUserCommand("Import Tester", username, username + "@test.local",
                "Password123", "ADMINISTRATOR", storeId));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"))));
        List<CategoryDto> categories = categoryFacade.listAll();
        drinks = categories.stream().filter(c -> c.name().equals("Bebidas")).findFirst().orElseThrow();
        groceries = categories.stream().filter(c -> c.name().equals("Alimentos y Abarrotes")).findFirst().orElseThrow();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from inventory_movements where store_id = ?", storeId);
        jdbcTemplate.update("delete from products where store_id = ?", storeId);
        jdbcTemplate.update("delete from product_subcategories where store_id = ?", storeId);
        jdbcTemplate.update("delete from users where username = ?", username);
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void import_createsProductsAndRecordsInitialStockInTheKardex() throws IOException {
        InputStream file = xlsx(
                row("P-1", "Arroz 500g", "Alimentos y Abarrotes", null, "7700000000011", 2500, 3200, 0, 40, 10, "Arroz blanco", null),
                row("P-2", "Gaseosa", "bebidas", null, null, 1800, 2500, 19, null, null, null, null));

        ProductImportResultDto result = productImportFacade.importProducts(file);

        assertEquals(2, result.created());
        assertEquals(1, result.stockMovements());
        ProductDto rice = byCode("P-1");
        assertEquals(40, rice.quantityAvailable());
        assertEquals(10, rice.minStock());
        assertEquals("Arroz blanco", rice.description());
        assertEquals(groceries.id(), rice.categoryId());
        ProductDto soda = byCode("P-2");
        assertEquals(0, soda.quantityAvailable());
        assertEquals(5, soda.minStock(), "stock mínimo por defecto, igual que el formulario");
        assertEquals(drinks.id(), soda.categoryId(), "la categoría se compara sin mayúsculas");
        assertEquals(0, new BigDecimal("19").compareTo(soda.taxRate()));
        assertNull(soda.barcode(), "un código de barras vacío se guarda como NULL, no como ''");

        List<InventoryMovementDto> movements = movementsOf(rice.id());
        assertEquals(1, movements.size());
        assertEquals("ENTRY", movements.get(0).movementType());
        assertEquals(40, movements.get(0).quantity());
        assertEquals(0, movements.get(0).previousStock());
        assertTrue(movementsOf(soda.id()).isEmpty());
    }

    @Test
    void import_updatesDataAndAppliesThePhysicalCountInTheSameRow() throws IOException {
        Long down = existing("C-1", "Leche", 3000, 4000, 10, "Leche entera");
        Long up = existing("C-2", "Pan", 500, 800, 10, null);

        ProductImportResultDto result = productImportFacade.importProducts(xlsx(
                row("C-1", null, null, null, null, null, 4200, null, 7, null, null, null),
                row("C-2", "Pan tajado", null, null, null, null, null, null, 15, null, null, null)));

        assertEquals(2, result.updated());
        assertEquals(2, result.stockMovements());
        // El stock sale de la BD: el update de datos no puede pisar el UPDATE atómico del ajuste (ni al revés).
        assertEquals(7, stockInDb(down));
        assertEquals(15, stockInDb(up));

        ProductDto milk = byCode("C-1");
        assertEquals(0, new BigDecimal("4200").compareTo(milk.salePrice()));
        assertEquals("Leche", milk.name(), "celda vacía = conservar el valor actual");
        assertEquals("Leche entera", milk.description(), "la descripción no se borra al actualizar");
        assertEquals("Pan tajado", byCode("C-2").name());

        InventoryMovementDto exit = movementsOf(down).get(0);
        assertEquals("SALIDA", exit.movementType());
        assertEquals(3, exit.quantity());
        assertEquals(10, exit.previousStock());
        assertEquals(7, exit.newStock());
        InventoryMovementDto adjustment = movementsOf(up).get(0);
        assertEquals("AJUSTE", adjustment.movementType());
        assertEquals(5, adjustment.quantity());
    }

    @Test
    void import_withOneInvalidRow_savesNothing() throws IOException {
        Long counted = existing("E-1", "Azúcar", 1000, 1500, 20, null);

        ProductImportRejectedException rejected = assertThrows(ProductImportRejectedException.class,
                () -> productImportFacade.importProducts(xlsx(
                        row("N-1", "Válido", "Bebidas", "Nueva sub", null, 1, 2, 0, 5, null, null, null),
                        row("E-1", null, null, null, null, null, null, null, 3, null, null, null),
                        row("N-2", "Inválido", "No existe", null, null, 1, 2, 0, 5, null, null, null))));

        assertEquals(1, rejected.getPreview().withErrors());
        ProductImportRowDto bad = rejected.getPreview().rows().get(2);
        assertEquals(4, bad.rowNumber());
        assertEquals("ERROR", bad.action());
        assertTrue(bad.errors().get(0).contains("no existe"));
        assertTrue(productFacade.findByInternalCodes(List.of("N-1", "N-2")).isEmpty());
        assertEquals(20, stockInDb(counted));
        assertTrue(movementsOf(counted).isEmpty());
        assertTrue(subcategoryFacade.listAll().isEmpty());
    }

    @Test
    void reimportingTheSameFile_changesNothing() throws IOException {
        Object[][] rows = {
                row("R-1", "Atún", "Alimentos y Abarrotes", "Enlatados", "7700000000028", 4000, 5500, 19, 12, 3, null, null),
                row("R-2", "Agua", "Bebidas", null, null, 900, 1500, 0, 30, null, "Botella", "50202301")};
        productImportFacade.importProducts(xlsx(rows));
        int movementsAfterFirst = movementCount();

        ProductImportResultDto second = productImportFacade.importProducts(xlsx(rows));

        assertEquals(0, second.created());
        assertEquals(0, second.updated());
        assertEquals(2, second.unchanged());
        assertEquals(0, second.stockMovements());
        assertEquals(0, second.subcategoriesCreated());
        assertEquals(movementsAfterFirst, movementCount());
        assertEquals(12, byCode("R-1").quantityAvailable());
    }

    @Test
    void import_createsEachMissingSubcategoryOnce() throws IOException {
        ProductImportResultDto result = productImportFacade.importProducts(xlsx(
                row("S-1", "Coca-Cola", "Bebidas", "Gaseosas", null, 1, 2, 0, null, null, null, null),
                row("S-2", "Pepsi", "Bebidas", "gaseosas", null, 1, 2, 0, null, null, null, null)));

        assertEquals(1, result.subcategoriesCreated());
        assertEquals(1, subcategoryFacade.listAll().size());
        assertEquals(byCode("S-1").subcategoryId(), byCode("S-2").subcategoryId());
        assertEquals("Gaseosas", byCode("S-2").subcategoryName());
    }

    @Test
    void preview_reportsEachProblemWithItsRowNumberAndWritesNothing() throws IOException {
        existing("B-1", "Con barras", 1, 2, 0, null, "7700000000035");

        ProductImportPreviewDto preview = productImportFacade.preview(xlsx(
                row("D-1", "Duplicado", "Bebidas", null, null, 1, 2, 0, null, null, null, null),
                row("D-1", "Duplicado", "Bebidas", null, null, 1, 2, 0, null, null, null, null),
                row("M-1", "Margen", "Bebidas", null, null, 5000, 4000, 0, null, null, null, null),
                row("V-1", null, null, null, null, null, "abc", 7, 2.5, null, null, null),
                row("X-1", "Barras ajeno", "Bebidas", null, "7700000000035", 1, 2, 0, null, null, null, null),
                row("OK-1", "Bien", "Bebidas", null, null, 1, 2, 0, null, null, null, null)));

        assertEquals(6, preview.totalRows());
        assertEquals(5, preview.withErrors());
        assertEquals(1, preview.toCreate());
        List<ProductImportRowDto> rows = preview.rows();
        assertTrue(rows.get(0).errors().get(0).contains("repetido en las filas 2, 3"));
        assertTrue(rows.get(2).errors().get(0).contains("menor que el precio de compra"));
        List<String> invalid = rows.get(3).errors();
        assertTrue(invalid.stream().anyMatch(e -> e.startsWith("Precio de venta: 'abc' no es un número")), invalid.toString());
        assertTrue(invalid.stream().anyMatch(e -> e.startsWith("IVA %: debe ser 0, 5 o 19")), invalid.toString());
        assertTrue(invalid.stream().anyMatch(e -> e.startsWith("Stock: debe ser un número entero")), invalid.toString());
        assertTrue(invalid.stream().anyMatch(e -> e.startsWith("Nombre es obligatorio")), invalid.toString());
        assertTrue(invalid.stream().noneMatch(e -> e.startsWith("Precio de venta es obligatorio")),
                "un valor inválido no se reporta además como vacío: " + invalid);
        assertTrue(rows.get(4).errors().get(0).contains("ya lo usa el producto B-1"));
        assertEquals("CREATE", rows.get(5).action());
        assertTrue(productFacade.findByInternalCodes(List.of("OK-1")).isEmpty());
    }

    @Test
    void numericCodesAreReadWithoutScientificNotation() throws IOException {
        productImportFacade.importProducts(xlsx(
                row(7701234567890d, "Numérico", "Bebidas", null, 7709876543210d, 1, 2, 0, null, null, null, null)));

        ProductDto product = byCode("7701234567890");
        assertEquals("7709876543210", product.barcode());
    }

    @Test
    void aCodeThatOnlyExistsInAnotherStore_isANewProduct() throws IOException {
        existing("SHARED-1", "De otro local", 1, 2, 4, null);
        TenantContext.setStoreId(STORE_IDS.incrementAndGet());
        Long otherStore = TenantContext.getStoreId();
        try {
            ProductImportPreviewDto preview = productImportFacade.preview(xlsx(
                    row("SHARED-1", "Mío", "Bebidas", null, null, 1, 2, 0, 1, null, null, null)));

            assertEquals("CREATE", preview.rows().get(0).action());
            assertNull(preview.rows().get(0).currentStock());
        } finally {
            jdbcTemplate.update("delete from products where store_id = ?", otherStore);
            TenantContext.setStoreId(storeId);
        }
    }

    @Test
    void templateWithProducts_reimportedAsIs_isAllUnchanged() throws IOException {
        existing("T-1", "Uno", 1, 2, 3, "Descripción uno");
        existing("T-2", "Dos", 1, 2, 0, null);

        byte[] template = productImportFacade.buildTemplate(true);
        ProductImportPreviewDto preview = productImportFacade.preview(new ByteArrayInputStream(template));

        assertEquals(2, preview.totalRows());
        assertEquals(2, preview.unchanged(), preview.rows().toString());
    }

    @Test
    void aFileWithoutTheCodeColumn_isRejected() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Productos");
            sheet.createRow(0).createCell(0).setCellValue("Nombre");
            sheet.createRow(1).createCell(0).setCellValue("Algo");
            workbook.write(out);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> productImportFacade.preview(new ByteArrayInputStream(out.toByteArray())));
            assertTrue(ex.getMessage().contains("Código interno"));
        }
    }

    @Test
    void aFileThatIsNotAnExcel_isRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> productImportFacade.preview(new ByteArrayInputStream("codigo;nombre\nP1;Algo".getBytes())));
        assertTrue(ex.getMessage().contains("no es un Excel"));
    }

    // ---------------------------------------------------------------- helpers

    private Long existing(String code, String name, int purchase, int sale, int stock, String description) {
        return existing(code, name, purchase, sale, stock, description, null);
    }

    private Long existing(String code, String name, int purchase, int sale, int stock, String description, String barcode) {
        ProductDto product = productFacade.createProduct(new CreateProductCommand(code, barcode, name, groceries.id(), null,
                BigDecimal.valueOf(purchase), BigDecimal.valueOf(sale), 5, 0, description, null, BigDecimal.ZERO, null));
        if (stock > 0) {
            inventoryFacade.registerMovement(product.id(), "ENTRY", stock, "Stock inicial");
        }
        jdbcTemplate.update("delete from inventory_movements where product_id = ?", product.id());
        return product.id();
    }

    private ProductDto byCode(String code) {
        return productFacade.findByInternalCodes(List.of(code)).stream().findFirst().orElseThrow();
    }

    private int stockInDb(Long productId) {
        return jdbcTemplate.queryForObject("select quantity_available from products where id = ?", Integer.class, productId);
    }

    private List<InventoryMovementDto> movementsOf(Long productId) {
        return inventoryFacade.searchMovements(productId, null, null, null, PageRequest.of(0, 50)).getContent();
    }

    private int movementCount() {
        return jdbcTemplate.queryForObject("select count(*) from inventory_movements where store_id = ?", Integer.class, storeId);
    }

    private static Object[] row(Object... values) {
        return values;
    }

    /** Archivo con la hoja Productos y los encabezados de la plantilla; los números van como celdas numéricas. */
    private static InputStream xlsx(Object[]... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Productos");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADER.length; i++) {
                header.createCell(i).setCellValue(HEADER[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}
