package com.sciencebot.pos.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda contra el error de V9: un {@code DROP CONSTRAINT IF EXISTS <nombre>} con un nombre que nunca existió
 * no falla y no hace nada, y la restricción vieja sigue vigente en producción. A partir de V25 todo DROP
 * ... IF EXISTS debe apuntar a una restricción declarada con nombre en alguna migración, o al nombre que
 * PostgreSQL genera solo para un UNIQUE en línea ({@code <tabla>_<columna>_key}).
 * V1..V24 quedan fuera: son migraciones ya aplicadas y no se editan (V25 corrige el caso de V9).
 */
class MigrationConstraintNamesTest {

    private static final int FIRST_CHECKED_VERSION = 25;
    private static final Pattern VERSION = Pattern.compile("^V([0-9]+)__.*[.]sql$");
    private static final Pattern DECLARED = Pattern.compile("CONSTRAINT[ \t\r\n]+([A-Za-z0-9_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_IF_EXISTS =
            Pattern.compile("DROP[ \t\r\n]+CONSTRAINT[ \t\r\n]+IF[ \t\r\n]+EXISTS[ \t\r\n]+([A-Za-z0-9_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PG_AUTO_UNIQUE_NAME = Pattern.compile("^[a-z0-9_]+_[a-z0-9_]+_key$");

    /** Quita los comentarios `-- ...` para que un texto explicativo no se lea como sentencia. */
    private static String stripComments(String sql) {
        return sql.replaceAll("--[^\r\n]*", "");
    }

    @Test
    void dropConstraintIfExistsTargetsARealConstraint() throws IOException {
        TreeMap<Integer, String> byVersion = new TreeMap<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Matcher m = VERSION.matcher(file.getFileName().toString());
                if (m.matches()) {
                    byVersion.put(Integer.parseInt(m.group(1)), stripComments(Files.readString(file)));
                }
            }
        }

        Set<String> declared = new HashSet<>();
        byVersion.values().forEach(sql -> {
            Matcher m = DECLARED.matcher(sql);
            while (m.find()) declared.add(m.group(1).toLowerCase(Locale.ROOT));
        });

        for (var entry : byVersion.entrySet()) {
            if (entry.getKey() < FIRST_CHECKED_VERSION) continue;
            Matcher m = DROP_IF_EXISTS.matcher(entry.getValue());
            while (m.find()) {
                String name = m.group(1).toLowerCase(Locale.ROOT);
                assertTrue(declared.contains(name) || PG_AUTO_UNIQUE_NAME.matcher(name).matches(),
                        "V" + entry.getKey() + " hace DROP CONSTRAINT IF EXISTS " + name
                                + ", pero ninguna migración declara una restricción con ese nombre");
            }
        }
    }
}
