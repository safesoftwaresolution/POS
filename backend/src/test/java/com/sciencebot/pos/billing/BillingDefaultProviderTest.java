package com.sciencebot.pos.billing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El proveedor por defecto de la aplicacion es Alegra. El perfil de test fija factus a proposito (src/test/resources),
 * asi que la configuracion real se lee de los archivos versionados: si alguien la cambia, este test avisa.
 */
class BillingDefaultProviderTest {

    private static String lineStartingWith(Path file, String prefix) throws IOException {
        return Files.readAllLines(file).stream().map(String::trim).filter(l -> l.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError(file + " no tiene una linea que empiece por " + prefix));
    }

    @Test
    void theApplicationDefaultsToAlegra() throws IOException {
        assertEquals("billing.provider=${BILLING_PROVIDER:alegra}",
                lineStartingWith(Path.of("src/main/resources/application.properties"), "billing.provider="));
    }

    @Test
    void theExampleEnvAndTheProductionComposeDefaultToAlegra() throws IOException {
        assertEquals("BILLING_PROVIDER=alegra", lineStartingWith(Path.of(".env.example"), "BILLING_PROVIDER="));
        assertTrue(lineStartingWith(Path.of("docker-compose.prod.yml"), "BILLING_PROVIDER:").contains(":-alegra}"));
    }

    @Test
    void theProductionComposePassesTheAlegraVariablesButNeverCarriesATokenValue() throws IOException {
        List<String> compose = Files.readAllLines(Path.of("docker-compose.prod.yml"));
        assertTrue(compose.stream().anyMatch(l -> l.contains("ALEGRA_API_TOKEN_PRODUCTION")));
        assertTrue(compose.stream().anyMatch(l -> l.contains("ALEGRA_API_TOKEN_SANDBOX")));
        // Solo referencias a variables de entorno (${...}); un token pegado a mano no puede quedar versionado.
        assertTrue(compose.stream().filter(l -> l.contains("ALEGRA_API_TOKEN")).allMatch(l -> l.contains("${")));
    }

    @Test
    void theExampleEnvNeverContainsAnAlegraTokenValue() throws IOException {
        for (String line : Files.readAllLines(Path.of(".env.example"))) {
            if (line.trim().startsWith("ALEGRA_API_TOKEN")) {
                assertEquals("", line.substring(line.indexOf('=') + 1).trim(), "el ejemplo no lleva tokens: " + line);
            }
        }
        assertFalse(Files.readString(Path.of(".env.example")).contains("eyJ"), "parece un token JWT pegado");
    }
}
