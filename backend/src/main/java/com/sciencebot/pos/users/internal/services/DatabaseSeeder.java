package com.sciencebot.pos.users.internal.services;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.users.CreateUserCommand;
import com.sciencebot.pos.users.UserFacade;
import com.sciencebot.pos.users.internal.repositories.UserRepository;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.customers.CreateCustomerCommand;
import com.sciencebot.pos.settings.SettingsFacade;
import com.sciencebot.pos.settings.SettingsDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Corre primero (Order 1): siembra el store/admin base del que dependen los demás seeders
 * (CategorySeeder Order 2, ProductDemoSeeder Order 3 en el perfil dev).
 */
@Component
@Order(1)
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final UserRepository userRepository;
    private final UserFacade userFacade;
    private final CustomerFacade customerFacade;
    private final SettingsFacade settingsFacade;
    private final Environment environment;
    private final String superAdminPassword;

    public DatabaseSeeder(
            UserRepository userRepository,
            UserFacade userFacade,
            CustomerFacade customerFacade,
            SettingsFacade settingsFacade,
            Environment environment,
            @Value("${app.seed.superadmin-password:}") String superAdminPassword) {
        this.userRepository = userRepository;
        this.userFacade = userFacade;
        this.customerFacade = customerFacade;
        this.settingsFacade = settingsFacade;
        this.environment = environment;
        this.superAdminPassword = superAdminPassword;
    }

    @Override
    public void run(String... args) {
        // SUPER_ADMIN: la clave nunca esta en el codigo. En dev viene de application-dev.properties;
        // en cualquier otro perfil hay que definir SEED_SUPERADMIN_PASSWORD (si no, no se siembra).
        if (!userRepository.existsByUsername("superadmin")) {
            if (superAdminPassword == null || superAdminPassword.isBlank()) {
                log.warn("No se siembra SUPER_ADMIN: defina SEED_SUPERADMIN_PASSWORD para crear la cuenta inicial.");
            } else {
                userFacade.createUser(new CreateUserCommand(
                        "Super Admin",
                        "superadmin",
                        "superadmin@platform.internal",
                        superAdminPassword,
                        "SUPER_ADMIN",
                        null
                ));
                log.info("SUPER_ADMIN 'superadmin' sembrado.");
            }
        }

        // Todo lo siguiente (admin de prueba con clave conocida, local 1, cliente y settings de
        // ejemplo) es solo para desarrollo: en otros perfiles los locales se crean por onboarding.
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            return;
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
            log.info("Administrador de desarrollo 'admin' sembrado (store_id=1).");
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
                log.info("Cliente General sembrado: 9999999999");
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