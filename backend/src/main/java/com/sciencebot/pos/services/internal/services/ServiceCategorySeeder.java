package com.sciencebot.pos.services.internal.services;

import com.sciencebot.pos.services.CreateServiceCategoryCommand;
import com.sciencebot.pos.services.ServiceCategoryFacade;
import com.sciencebot.pos.services.internal.repositories.ServiceCategoryRepository;
import com.sciencebot.pos.stores.StoreCategoryDto;
import com.sciencebot.pos.stores.StoreFacade;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Siembra las categorías de servicios de los negocios con agenda de citas y las enlaza a sus categorías de local
 * (después de StoreCategorySeeder). Idempotente: solo crea las que no existen, así el SUPER_ADMIN puede renombrarlas o
 * cambiar sus vínculos sin que se vuelvan a sembrar. Una categoría sin locales enlazados queda disponible para todos.
 */
@Component
@Order(3)
public class ServiceCategorySeeder implements CommandLineRunner {

    private static final String BARBERSHOP = "Barbería";
    private static final String HAIR_SALON = "Peluquería";
    private static final String NAIL_SALON = "Salón de uñas y pies";
    private static final String BOUTIQUE = "Boutique";

    /** Nombre, descripción y categorías de local donde se ofrece (vacío = todas). */
    record Seed(String name, String description, List<String> storeCategories) {}

    static final List<Seed> BASE_CATEGORIES = List.of(
            new Seed("Corte", "Cortes de cabello", List.of(BARBERSHOP, HAIR_SALON)),
            new Seed("Barba", "Arreglo y perfilado de barba", List.of(BARBERSHOP)),
            new Seed("Coloración y tratamientos", "Tintes, alisados y tratamientos capilares", List.of(HAIR_SALON)),
            new Seed("Peinado", "Peinados y recogidos", List.of(HAIR_SALON)),
            new Seed("Maquillaje", "Maquillaje social y de eventos", List.of(HAIR_SALON, NAIL_SALON, BOUTIQUE)),
            new Seed("Manicure y pedicure", "Cuidado y esmaltado de manos y pies", List.of(NAIL_SALON)),
            new Seed("Arreglos de ropa", "Ajustes y arreglos de prendas", List.of(BOUTIQUE)),
            new Seed("Asesoría de imagen", "Asesoría de estilo y vestuario", List.of(BOUTIQUE)),
            new Seed("Otros servicios", "Servicios que no encajan en las demás categorías", List.of())
    );

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceCategoryFacade categoryFacade;
    private final StoreFacade storeFacade;

    public ServiceCategorySeeder(ServiceCategoryRepository categoryRepository,
                                 ServiceCategoryFacade categoryFacade,
                                 StoreFacade storeFacade) {
        this.categoryRepository = categoryRepository;
        this.categoryFacade = categoryFacade;
        this.storeFacade = storeFacade;
    }

    @Override
    public void run(String... args) {
        Map<String, Long> storeCategoryIds = storeFacade.listCategories().stream()
                .collect(Collectors.toMap(StoreCategoryDto::name, StoreCategoryDto::id, (a, b) -> a));
        int created = 0;
        for (Seed seed : BASE_CATEGORIES) {
            if (categoryRepository.existsByNameIgnoreCase(seed.name())) {
                continue;
            }
            List<Long> links = seed.storeCategories().stream().map(storeCategoryIds::get).filter(java.util.Objects::nonNull).toList();
            // Si ninguna de sus categorías de local existe, sembrarla sin vínculos la dejaría abierta a todos: se omite.
            if (links.isEmpty() && !seed.storeCategories().isEmpty()) {
                continue;
            }
            categoryFacade.create(new CreateServiceCategoryCommand(seed.name(), seed.description(), links));
            created++;
        }
        if (created > 0) {
            System.out.println("==================================================");
            System.out.println("Categorías de servicios sembradas: " + created + " nuevas");
            System.out.println("==================================================");
        }
    }
}
