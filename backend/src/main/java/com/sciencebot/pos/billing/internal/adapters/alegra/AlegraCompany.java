package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Empresa asociada en Alegra (lo que devuelve {@code GET /companies/{id}}). Solo los datos que el sistema usa;
 * los campos nuevos que agregue Alegra se ignoran.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlegraCompany(
        String id,
        String name,
        String tradeName,
        String identification,
        String dv,
        String regimeCode,
        Integer organizationType,
        String identificationType,
        String email,
        String phone,
        Address address,
        Map<String, String> governmentStatus
) {

    /** Dirección de la empresa en Alegra. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(String address, String city, String department, String country) {}
}
