package com.sciencebot.pos.users;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El login normaliza a minúsculas lo que escribe la persona, así que un usuario registrado con mayúsculas
 * ("AndresV") debe encontrarse igual: antes la búsqueda era exacta y devolvía 401 con la contraseña correcta.
 */
@SpringBootTest
@Transactional
class UserLookupCaseInsensitiveTest {

    @Autowired private UserFacade userFacade;
    @Autowired private UserDetailsService userDetailsService;

    @Test
    void findByUsername_IgnoresCase() {
        // 'admin' es sembrado por DatabaseSeeder al arrancar el contexto.
        UserDto exact = userFacade.findByUsername("admin").orElseThrow();

        assertEquals(exact.id(), userFacade.findByUsername("ADMIN").orElseThrow().id());
        assertEquals(exact.id(), userFacade.findByUsername("Admin").orElseThrow().id());
        assertEquals(exact.username(), userDetailsService.loadUserByUsername("AdMiN").getUsername());
    }

    @Test
    void findByEmail_IgnoresCase() {
        UserDto exact = userFacade.findByUsername("admin").orElseThrow();

        assertEquals(exact.id(), userFacade.findByEmail(exact.email().toUpperCase()).orElseThrow().id());
    }

    @Test
    void registerPendingAdmin_RejectsAUsernameOrEmailThatOnlyDiffersInCase() {
        UserDto exact = userFacade.findByUsername("admin").orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> userFacade.registerPendingAdmin(
                new RegisterOwnerCommand("Otro", "ADMIN", "otro@test.com", "Password123")));
        assertThrows(IllegalArgumentException.class, () -> userFacade.registerPendingAdmin(
                new RegisterOwnerCommand("Otro", "otro_usuario", exact.email().toUpperCase(), "Password123")));
    }
}
