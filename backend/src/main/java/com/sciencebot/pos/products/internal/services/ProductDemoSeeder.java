package com.sciencebot.pos.products.internal.services;

import com.sciencebot.pos.categories.CategoryDto;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.products.CreateProductCommand;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.products.internal.repositories.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Solo perfil dev: siembra productos de ejemplo en la tienda 1 (la misma sembrada por
 * DatabaseSeeder, Order 1) para poder probar el catálogo y el escaneo de código de barras en
 * el POS localmente sin cargar datos manualmente. Corre después de CategorySeeder (Order 2)
 * porque necesita que las categorías base ya existan.
 */
@Component
@Profile("dev")
@Order(3)
public class ProductDemoSeeder implements CommandLineRunner {

    private static final Long DEMO_STORE_ID = 1L;

    private record DemoProduct(String internalCode, String barcode, String name, String categoryName,
                                BigDecimal purchasePrice, BigDecimal salePrice, int stock, int minStock) {}

    private static final List<DemoProduct> DEMO_PRODUCTS = List.of(
            new DemoProduct("PROD-001", "7702001000012", "Arroz Diana 500g", "Alimentos y Abarrotes", bd(2100), bd(3200), 80, 15),
            new DemoProduct("PROD-002", "7702001000029", "Aceite Girasol 1L", "Alimentos y Abarrotes", bd(8500), bd(12500), 40, 10),
            new DemoProduct("PROD-003", "7702001000036", "Azúcar Blanca 1kg", "Alimentos y Abarrotes", bd(2800), bd(4000), 60, 10),
            new DemoProduct("PROD-004", "7702001000043", "Coca-Cola 1.5L", "Bebidas", bd(3200), bd(5000), 50, 12),
            new DemoProduct("PROD-005", "7702001000050", "Agua Cristal 600ml", "Bebidas", bd(900), bd(1500), 100, 20),
            new DemoProduct("PROD-006", "7702001000067", "Jugo Hit Naranja 200ml", "Bebidas", bd(1100), bd(1800), 70, 15),
            new DemoProduct("PROD-007", "7702001000074", "Café Águila Roja 250g", "Bebidas", bd(6500), bd(9500), 3, 10),
            new DemoProduct("PROD-008", "7702001000081", "Jabón en Barra Rey 300g", "Aseo y Limpieza", bd(1800), bd(2800), 45, 10),
            new DemoProduct("PROD-009", "7702001000098", "Detergente Fab 500g", "Aseo y Limpieza", bd(3200), bd(4800), 30, 8),
            new DemoProduct("PROD-010", "7702001000104", "Papel Higiénico x4", "Aseo y Limpieza", bd(4200), bd(6200), 25, 8),
            new DemoProduct("PROD-011", "7702001000111", "Shampoo Savital 400ml", "Cuidado Personal", bd(6800), bd(9900), 20, 6),
            new DemoProduct("PROD-012", "7702001000128", "Crema Dental Colgate 90g", "Cuidado Personal", bd(3100), bd(4600), 35, 10),
            new DemoProduct("PROD-013", "7702001000135", "Papas Margarita 30g", "Snacks y Dulces", bd(900), bd(1600), 90, 20),
            new DemoProduct("PROD-014", "7702001000142", "Chocolatina Jet", "Snacks y Dulces", bd(700), bd(1200), 0, 15),
            new DemoProduct("PROD-015", "7702001000159", "Galletas Festival x6", "Snacks y Dulces", bd(2200), bd(3400), 40, 10),
            new DemoProduct("PROD-016", "7702001000166", "Leche Entera Alquería 1L", "Lácteos y Refrigerados", bd(3400), bd(4700), 25, 10),
            new DemoProduct("PROD-017", "7702001000173", "Queso Campesino 250g", "Lácteos y Refrigerados", bd(5200), bd(7500), 15, 5),
            new DemoProduct("PROD-018", "7702001000180", "Pan Tajado Bimbo", "Panadería", bd(4500), bd(6500), 18, 6),
            new DemoProduct("PROD-019", "7702001000197", "Bombillo LED 9W", "Hogar y Ferretería Menor", bd(4800), bd(7200), 22, 5),
            new DemoProduct("PROD-020", "7702001000203", "Cuaderno 100 hojas", "Papelería y Oficina", bd(2600), bd(4000), 30, 8)
    );

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }

    private final ProductRepository productRepository;
    private final ProductFacade productFacade;
    private final CategoryFacade categoryFacade;

    public ProductDemoSeeder(ProductRepository productRepository, ProductFacade productFacade, CategoryFacade categoryFacade) {
        this.productRepository = productRepository;
        this.productFacade = productFacade;
        this.categoryFacade = categoryFacade;
    }

    @Override
    public void run(String... args) {
        TenantContext.setStoreId(DEMO_STORE_ID);
        try {
            Map<String, Long> categoryIdsByName = categoryFacade.listAll().stream()
                    .collect(java.util.stream.Collectors.toMap(CategoryDto::name, CategoryDto::id));

            int created = 0;
            for (DemoProduct demo : DEMO_PRODUCTS) {
                if (productRepository.existsByStoreIdAndInternalCode(DEMO_STORE_ID, demo.internalCode())) {
                    continue;
                }
                Long categoryId = categoryIdsByName.get(demo.categoryName());
                if (categoryId == null) {
                    continue; // categoría base no sembrada todavía; se omite este producto demo
                }

                var createdProduct = productFacade.createProduct(new CreateProductCommand(
                        demo.internalCode(),
                        demo.barcode(),
                        demo.name(),
                        categoryId,
                        null,
                        demo.purchasePrice(),
                        demo.salePrice(),
                        demo.minStock(),
                        null,
                        null
                ));
                if (demo.stock() > 0) {
                    productFacade.updateStock(createdProduct.id(), demo.stock());
                }
                created++;
            }
            if (created > 0) {
                System.out.println("==================================================");
                System.out.println("Productos demo sembrados en tienda " + DEMO_STORE_ID + ": " + created + " nuevos");
                System.out.println("==================================================");
            }
        } finally {
            TenantContext.clear();
        }
    }
}
