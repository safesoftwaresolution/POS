package com.sciencebot.pos.config.json;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;

/**
 * Jackson 3 (Spring Boot 4) rechaza por defecto un campo primitivo ausente del JSON con "Cannot map `null` into
 * type `int`" (400 "El cuerpo de la solicitud JSON es invalido"). Los comandos del proyecto son records con
 * primitivos opcionales (por ejemplo {@code int quantityAvailable} en CreateProductCommand, que el formulario de
 * producto no envia porque el stock inicial se registra como movimiento) y asumen el comportamiento clasico:
 * ausente = 0 / false.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer lenientPrimitivesCustomizer() {
        return builder -> builder.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
}
