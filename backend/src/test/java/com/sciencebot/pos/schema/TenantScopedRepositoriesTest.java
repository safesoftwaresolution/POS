package com.sciencebot.pos.schema;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Red de seguridad del aislamiento por local: el filtro por {@code store_id} es manual en cada servicio
 * (ver CLAUDE.md, "Multi-tenancy"), asi que un metodo nuevo de repositorio que olvide el local filtraria
 * datos de otros locales. Este test revisa todo repositorio cuya entidad tiene {@code storeId} y exige que
 * cada metodo declarado lo mencione: en su nombre derivado (...StoreId...) o en su {@code @Query}.
 *
 * Un metodo legitimamente global (por ejemplo, un job de plataforma o una busqueda por token unico) se
 * agrega a {@link #PLATFORM_WIDE} con su motivo; asi la excepcion queda visible en la revision.
 */
class TenantScopedRepositoriesTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    /** "Repositorio.metodo" que a proposito no filtra por local. Cada entrada lleva su motivo. */
    private static final Set<String> PLATFORM_WIDE = Set.of(
            // Clave unica global (reference_code, idempotency_key, numero de ticket/nota): el servicio compara
            // el storeId de lo encontrado con el del contexto y responde "en uso"/"no existe" si no coincide.
            "ElectronicCreditNoteRepository.findByReferenceCodeForUpdate",
            "ElectronicCreditNoteRepository.findByReferenceCode",
            "ElectronicCreditNoteRepository.findByCreditNoteNumber",
            "ElectronicDebitNoteRepository.findByReferenceCodeForUpdate",
            "ElectronicDebitNoteRepository.findByReferenceCode",
            "ElectronicDebitNoteRepository.findByDebitNoteNumber",
            "PurchaseRepository.findByIdempotencyKey",
            "SaleRepository.findByIdempotencyKey",
            "SupportTicketRepository.findByTicketNumber",
            // invoice_seq es una secuencia global a proposito: el numero FACT-%06d es unico en toda la plataforma.
            "SaleRepository.getNextInvoiceSeq",
            // Se invocan solo despues de validar la venta/factura del local actual (id ya verificado).
            "ElectronicCreditNoteRepository.findBySaleId",
            "ElectronicDebitNoteRepository.findBySaleId",
            "ElectronicInvoiceRepository.findBySaleId",
            "ElectronicInvoiceRepository.findBySaleIdForUpdate",
            "SaleReturnRepository.findAllBySaleIdOrderByCreatedAtDesc",
            "SaleReturnRepository.sumReturnedQuantityBySaleItemId",
            "ProductRepository.findStockById",
            // adjustStock se usa solo sin local (llamadas internas de plataforma); con local va adjustStockInStore.
            "ProductRepository.adjustStock",
            // Jobs y metricas de plataforma (SUPER_ADMIN, sin local en contexto).
            "ElectronicInvoiceRepository.findByStatus",
            "StoreBillingConfigRepository.findByStatusOrderByUpdatedAtAsc",
            "SupportTicketRepository.countByStatus",
            "SupportTicketRepository.countByTypeAndPriority",
            // El catalogo de categorias es global y solo lo escribe SUPER_ADMIN: contar por categoria es global.
            "SubcategoryRepository.existsByCategoryId",
            "ProductRepository.countByCategoryIdAndActiveTrue",
            "ProductRepository.existsByCategoryIdAndActiveTrue",
            "ProductRepository.countBySubcategoryIdAndActiveTrue",
            "ProductRepository.existsBySubcategoryIdAndActiveTrue",
            // Usuarios: login/unicidad globales (username y email son UNIQUE en toda la plataforma) y listados
            // de SUPER_ADMIN; UserServiceImpl usa las variantes ...StoreId... cuando hay local en contexto.
            "UserRepository.findByUsername",
            "UserRepository.findByEmail",
            "UserRepository.existsByUsername",
            "UserRepository.existsByEmail",
            "UserRepository.findAllByActiveTrue",
            "UserRepository.findAllByRole",
            "UserRepository.findAllByRoleAndEmailVerifiedFalse",
            "UserRepository.findAllByRoleAndEmailVerifiedTrue",
            "UserRepository.countByRoleAndActiveTrue");

    @Test
    void everyQueryOnATenantScopedEntityFiltersByStore() throws Exception {
        List<String> violations = new ArrayList<>();
        int checkedRepositories = 0;

        for (Class<?> repository : repositories()) {
            Class<?> entity = entityOf(repository);
            if (entity == null || !hasStoreId(entity)) {
                continue;
            }
            checkedRepositories++;
            for (Method method : repository.getDeclaredMethods()) {
                String id = repository.getSimpleName() + "." + method.getName();
                if (!mentionsStore(method) && !PLATFORM_WIDE.contains(id)) {
                    violations.add(id);
                }
            }
        }

        assertThat(checkedRepositories)
                .as("el test debe encontrar los repositorios con storeId (cambio de paquetes?)")
                .isGreaterThan(5);
        assertThat(violations)
                .as("Metodos de repositorios con store_id que no filtran por local. Agrega StoreId al metodo o "
                        + "al @Query; si es global a proposito, listalo en PLATFORM_WIDE con su motivo")
                .isEmpty();
    }

    private static boolean mentionsStore(Method method) {
        Query query = method.getAnnotation(Query.class);
        if (query != null) {
            String text = query.value().toLowerCase();
            return text.contains("storeid") || text.contains("store_id") || text.contains("store.id");
        }
        return method.getName().contains("StoreId");
    }

    private static boolean hasStoreId(Class<?> entity) {
        for (Class<?> type = entity; type != null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (field.getName().equals("storeId")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Class<?> entityOf(Class<?> repository) {
        for (Type type : repository.getGenericInterfaces()) {
            if (type instanceof ParameterizedType p && p.getRawType() == JpaRepository.class
                    && p.getActualTypeArguments()[0] instanceof Class<?> entity) {
                return entity;
            }
        }
        return null;
    }

    private static List<Class<?>> repositories() throws Exception {
        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith("Repository.java")).toList()) {
                String relative = MAIN_SOURCES.relativize(file).toString().replace('\\', '/');
                String className = relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
                Class<?> type = Class.forName(className);
                if (type.isInterface() && JpaRepository.class.isAssignableFrom(type)) {
                    found.add(type);
                }
            }
        }
        return found;
    }
}
