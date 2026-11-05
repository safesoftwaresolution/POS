package com.sciencebot.pos.stores.internal.services;

import com.sciencebot.pos.stores.CreateStoreCategoryCommand;
import com.sciencebot.pos.stores.StoreFacade;
import com.sciencebot.pos.stores.internal.repositories.StoreCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Siembra los tipos de negocio con los que se clasifican los locales registrados en la
 * plataforma (pantalla "Categorías de Locales" del backoffice). No confundir con las categorías
 * de producto (CategorySeeder). Cada categoría trae su plantilla (RETAIL, FOOD, APPOINTMENTS) y su tema visual. Corre en cualquier perfil y es idempotente: solo crea las que
 * no existen, así que el SUPER_ADMIN puede renombrar o desactivar sin que se dupliquen.
 */
@Component
@Order(2)
public class StoreCategorySeeder implements CommandLineRunner {

    private static final List<CreateStoreCategoryCommand> BASE_CATEGORIES = List.of(
            new CreateStoreCategoryCommand("Tienda de barrio / Minimercado", "Tiendas de barrio y minimercados", "RETAIL", "GROCERY"),
            new CreateStoreCategoryCommand("Supermercado", "Supermercados y autoservicios", "RETAIL", "GROCERY"),
            new CreateStoreCategoryCommand("Droguería / Farmacia", "Droguerías y farmacias", "RETAIL", "PHARMACY"),
            new CreateStoreCategoryCommand("Panadería", "Panaderías y pastelerías", "RETAIL", "BAKERY"),
            new CreateStoreCategoryCommand("Papelería", "Papelerías y artículos de oficina", "RETAIL", "STATIONERY"),
            new CreateStoreCategoryCommand("Ferretería", "Ferreterías y materiales", "RETAIL", "HARDWARE"),
            new CreateStoreCategoryCommand("Restaurante / Cafetería", "Restaurantes, cafeterías y comidas", "FOOD", "FOOD"),
            new CreateStoreCategoryCommand("Licorera", "Licoreras y distribuidoras de bebidas", "RETAIL", "LIQUOR"),
            new CreateStoreCategoryCommand("Tienda de mascotas", "Alimento y accesorios para mascotas", "RETAIL", "PETS"),
            new CreateStoreCategoryCommand("Barbería", "Barberías con agenda de citas", "APPOINTMENTS", "BEAUTY"),
            new CreateStoreCategoryCommand("Peluquería", "Peluquerías y salones de belleza con agenda de citas", "APPOINTMENTS", "BEAUTY"),
            new CreateStoreCategoryCommand("Salón de uñas y pies", "Salones de manicure y pedicure con agenda de citas", "APPOINTMENTS", "BEAUTY"),
            new CreateStoreCategoryCommand("Boutique", "Boutiques con agenda de citas", "APPOINTMENTS", "FASHION"),
            new CreateStoreCategoryCommand("Otros", "Negocios que no encajan en las demás categorías", "RETAIL", "DEFAULT")
    );

    private final StoreCategoryRepository storeCategoryRepository;
    private final StoreFacade storeFacade;

    public StoreCategorySeeder(StoreCategoryRepository storeCategoryRepository, StoreFacade storeFacade) {
        this.storeCategoryRepository = storeCategoryRepository;
        this.storeFacade = storeFacade;
    }

    @Override
    public void run(String... args) {
        int created = 0;
        for (CreateStoreCategoryCommand command : BASE_CATEGORIES) {
            if (!storeCategoryRepository.existsByNameIgnoreCase(command.name())) {
                storeFacade.createCategory(command);
                created++;
            }
        }
        if (created > 0) {
            System.out.println("==================================================");
            System.out.println("Categorías de locales sembradas: " + created + " nuevas");
            System.out.println("==================================================");
        }
    }
}
