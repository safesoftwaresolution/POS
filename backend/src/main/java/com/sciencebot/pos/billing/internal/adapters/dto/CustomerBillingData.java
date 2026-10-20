package com.sciencebot.pos.billing.internal.adapters.dto;

import java.util.List;

public record CustomerBillingData(
        String identificationDocumentCode,
        String identification,
        String dv,
        String legalOrganizationCode,
        String tributeCode,
        List<String> responsibilities,
        String names,
        String company,
        String tradeName,
        String email,
        String phone,
        String address,
        String countryCode,
        String municipalityCode
) {
    // Constructor de conveniencia para compatibilidad con llamadas existentes
    public CustomerBillingData(
            String identification,
            String dv,
            String legalName,
            String email,
            Integer legalOrganizationId,
            Integer tributeId,
            Integer municipalityId
    ) {
        this(
                (identification != null && identification.length() == 9 && dv != null && !dv.isBlank()) ? "31" : "13",
                identification,
                dv,
                (legalOrganizationId != null && legalOrganizationId == 1) ? "1" : "2",
                (tributeId != null && tributeId == 1) ? "01" : "ZZ",
                List.of("R-99-PN"),
                (legalOrganizationId != null && legalOrganizationId == 1) ? null : legalName,
                (legalOrganizationId != null && legalOrganizationId == 1) ? legalName : null,
                null,
                email,
                null,
                null,
                "CO",
                municipalityId != null ? String.valueOf(municipalityId) : null
        );
    }
}
