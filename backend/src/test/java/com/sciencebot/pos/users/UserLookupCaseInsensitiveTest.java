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

    // El perfil test no siembra 'admin' (DatabaseSeeder solo lo hace en dev): cada test registra el suyo.
    private UserDto registerMixedCaseUser() {
        return userFacade.registerPendingAdmin(
                new RegisterOwnerCommand("Andres V", "AndresV", "AndresV@Test.com", "Password123"));
    }

    @Test
    void findByUsername_IgnoresCase() {
        UserDto exact = registerMixedCaseUser();

        assertEquals(exact.id(), userFacade.findByUsername("andresv").orElseThrow().id());
        assertEquals(exact.id(), userFacade.findByUsername("ANDRESV").orElseThrow().id());
        assertEquals(exact.username(), userDetailsService.loadUserByUsername("aNdReSv").getUsername());
    }

    @Test
    void findByEmail_IgnoresCase() {
        UserDto exact = registerMixedCaseUser();

        assertEquals(exact.id(), userFacade.findByEmail(exact.email().toLowerCase()).orElseThrow().id());
        assertEquals(exact.id(), userFacade.findByEmail(exact.email().toUpperCase()).orElseThrow().id());
    }

    @Test
    void registerPendingAdmin_RejectsAUsernameOrEmailThatOnlyDiffersInCase() {
        UserDto exact = registerMixedCaseUser();

        assertThrows(IllegalArgumentException.class, () -> userFacade.registerPendingAdmin(
                new RegisterOwnerCommand("Otro", "ANDRESV", "otro@test.com", "Password123")));
        assertThrows(IllegalArgumentException.class, () -> userFacade.registerPendingAdmin(
                new RegisterOwnerCommand("Otro", "otro_usuario", exact.email().toUpperCase(), "Password123")));
    }
}
