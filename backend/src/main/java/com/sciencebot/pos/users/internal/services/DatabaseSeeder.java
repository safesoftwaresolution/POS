package com.sciencebot.pos.users.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.users.CreateUserCommand;
import com.sciencebot.pos.users.UserFacade;
import com.sciencebot.pos.users.internal.repositories.UserRepository;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.customers.CreateCustomerCommand;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.settings.SettingsDto;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Corre primero (Order 1): siembra el store/admin base del que dependen los demás seeders
 * (CategorySeeder Order 2, ProductDemoSeeder Order 3 en el perfil dev).
 */
@Component
@Order(1)
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserFacade userFacade;
    private final CustomerFacade customerFacade;
    private final SettingsFacade settingsFacade;

    public DatabaseSeeder(
            UserRepository userRepository,
            UserFacade userFacade,
            CustomerFacade customerFacade,
            SettingsFacade settingsFacade) {
        this.userRepository = userRepository;
        this.userFacade = userFacade;
        this.customerFacade = customerFacade;
        this.settingsFacade = settingsFacade;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("superadmin")) {
            userFacade.createUser(new CreateUserCommand(
                    "Super Admin",
                    "superadmin",
                    "superadmin@platform.internal",
                    "SuperAdmin123!",
                    "SUPER_ADMIN",
                    null
            ));
            System.out.println("==================================================");
            System.out.println("SUPER_ADMIN seeded: superadmin / SuperAdmin123!");
            System.out.println("==================================================");
        }

        if (!userRepository.existsByUsername("admin")) {
            userFacade.createUser(new CreateUserCommand(
                    "Administrator",
                    "admin",
                    "admin@tienda.com",
                    "Password123",
                    "ADMINISTRATOR",
                    1L
            ));
            System.out.println("==================================================");
            System.out.println("Default administrator seeded: admin / Password123 (store_id=1)");
            System.out.println("==================================================");
        }

        // El seeder corre en el arranque, fuera de cualquier request HTTP, asi que no hay
        // TenantContext poblado por el JwtAuthenticationFilter. Se fija manualmente al local
        // por defecto (store_id=1, el mismo que se usa arriba para el admin sembrado) para que
        // los servicios de customers/settings (que ahora dependen de TenantContext) sepan a
        // que local pertenece lo que se esta sembrando.
        TenantContext.setStoreId(1L);
        try {
            if (customerFacade.getByIdentification("9999999999").isEmpty()) {
                customerFacade.createCustomer(new CreateCustomerCommand(
                        "Cliente General",
                        "9999999999",
                        null, null, null
                ));
                System.out.println("==================================================");
                System.out.println("General Customer seeded: 9999999999");
                System.out.println("==================================================");
            }

            // seedSettings ya valida internamente si el local todavia no tiene fila propia
            // (existsById) antes de crearla, asi que es seguro llamarla siempre en el arranque.
            settingsFacade.seedSettings(new SettingsDto(
                    "Mi Tienda",
                    "Calle Principal 123",
                    "555-0001",
                    "900111222-3",
                    "info@mitienda.com",
                    null
            ));
        } finally {
            TenantContext.clear();
        }
    }
}