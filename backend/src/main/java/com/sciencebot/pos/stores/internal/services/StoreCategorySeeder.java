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
 * de producto (CategorySeeder). Corre en cualquier perfil y es idempotente: solo crea las que
 * no existen, así que el SUPER_ADMIN puede renombrar o desactivar sin que se dupliquen.
 */
@Component
@Order(2)
public class StoreCategorySeeder implements CommandLineRunner {

    private static final List<CreateStoreCategoryCommand> BASE_CATEGORIES = List.of(
            new CreateStoreCategoryCommand("Tienda de barrio / Minimercado", "Tiendas de barrio y minimercados"),
            new CreateStoreCategoryCommand("Supermercado", "Supermercados y autoservicios"),
            new CreateStoreCategoryCommand("Droguería / Farmacia", "Droguerías y farmacias"),
            new CreateStoreCategoryCommand("Panadería", "Panaderías y pastelerías"),
            new CreateStoreCategoryCommand("Papelería", "Papelerías y artículos de oficina"),
            new CreateStoreCategoryCommand("Ferretería", "Ferreterías y materiales"),
            new CreateStoreCategoryCommand("Restaurante / Cafetería", "Restaurantes, cafeterías y comidas"),
            new CreateStoreCategoryCommand("Licorera", "Licoreras y distribuidoras de bebidas"),
            new CreateStoreCategoryCommand("Tienda de mascotas", "Alimento y accesorios para mascotas"),
            new CreateStoreCategoryCommand("Otros", "Negocios que no encajan en las demás categorías")
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
