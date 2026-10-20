package com.sciencebot.pos.categories.internal.services;

import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.categories.CreateCategoryCommand;
import com.sciencebot.pos.categories.internal.repositories.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Siembra la taxonomía base de categorías: son globales (compartidas por todas las tiendas,
 * ver categories/internal/entities/Category.java) y sirven como punto de partida genérico que
 * cada tienda puede renombrar/ampliar libremente desde /categories. Corre en cualquier perfil
 * (igual que el admin/cliente general sembrados en DatabaseSeeder) porque es catálogo base
 * real, no datos de demo.
 */
@Component
@Order(2)
public class CategorySeeder implements CommandLineRunner {

    private static final List<CreateCategoryCommand> BASE_CATEGORIES = List.of(
            new CreateCategoryCommand("Bebidas", "Gaseosas, jugos, agua, bebidas calientes"),
            new CreateCategoryCommand("Alimentos y Abarrotes", "Granos, enlatados, aceites y despensa en general"),
            new CreateCategoryCommand("Aseo y Limpieza", "Productos de limpieza para el hogar"),
            new CreateCategoryCommand("Cuidado Personal", "Higiene y cuidado personal"),
            new CreateCategoryCommand("Snacks y Dulces", "Pasabocas, galletas, confitería"),
            new CreateCategoryCommand("Lácteos y Refrigerados", "Leche, quesos, embutidos y productos que requieren frío"),
            new CreateCategoryCommand("Panadería", "Pan y productos de panadería"),
            new CreateCategoryCommand("Hogar y Ferretería Menor", "Artículos varios para el hogar y ferretería básica"),
            new CreateCategoryCommand("Papelería y Oficina", "Artículos de papelería y oficina"),
            new CreateCategoryCommand("Mascotas", "Alimento y accesorios para mascotas"),
            new CreateCategoryCommand("Otros / Varios", "Productos que no encajan en las demás categorías")
    );

    private final CategoryRepository categoryRepository;
    private final CategoryFacade categoryFacade;

    public CategorySeeder(CategoryRepository categoryRepository, CategoryFacade categoryFacade) {
        this.categoryRepository = categoryRepository;
        this.categoryFacade = categoryFacade;
    }

    @Override
    public void run(String... args) {
        int created = 0;
        for (CreateCategoryCommand command : BASE_CATEGORIES) {
            if (!categoryRepository.existsByNameIgnoreCase(command.name())) {
                categoryFacade.createCategory(command);
                created++;
            }
        }
        if (created > 0) {
            System.out.println("==================================================");
            System.out.println("Categorías base sembradas: " + created + " nuevas");
            System.out.println("==================================================");
        }
    }
}
