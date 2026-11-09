package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.DivipolaCatalogDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Catálogo DIVIPOLA (DANE) cargado una sola vez al arrancar desde {@code billing/divipola.json}. Si el archivo falta
 * o está dañado, la aplicación no arranca: es un recurso del propio código, no un dato del usuario.
 */
@Component
public class DivipolaCatalog {

    static final String RESOURCE = "billing/divipola.json";

    private final DivipolaCatalogDto catalog;

    public DivipolaCatalog() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            this.catalog = JsonMapper.builder().build().readValue(in, DivipolaCatalogDto.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el catálogo DIVIPOLA (" + RESOURCE + ")", ex);
        }
        if (catalog.departments().isEmpty() || catalog.municipalities().isEmpty()) {
            throw new IllegalStateException("El catálogo DIVIPOLA (" + RESOURCE + ") está vacío");
        }
    }

    public DivipolaCatalogDto get() {
        return catalog;
    }
}
