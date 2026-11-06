package com.sciencebot.pos.billing.internal.adapters.alegra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.exception.AlegraApiException;
import com.sciencebot.pos.billing.exception.BillingNotEnabledException;
import com.sciencebot.pos.billing.internal.adapters.BillingOnboardingProvider;
import com.sciencebot.pos.billing.internal.adapters.dto.CompanyProfileData;
import com.sciencebot.pos.billing.internal.adapters.dto.DianEnablementStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.DianResolutionData;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Alta del local en Alegra con el token de plataforma del entorno ({@link AlegraAccessResolver#platformAccess}):
 * crea y actualiza la empresa asociada ({@code POST/PATCH /companies}), envía el set de pruebas de facturas a la
 * DIAN ({@code POST /test-sets}), lee la habilitación ({@code governmentStatus.invoices}) y lista las resoluciones
 * del NIT ({@code GET /resolutions/{nit}}, solo producción). Las empresas usan el certificado de Alegra, así que el
 * dueño no sube ningún documento.
 */
@Component
@ConditionalOnProperty(name = "billing.provider", havingValue = "alegra")
public class AlegraOnboardingClient implements BillingOnboardingProvider {

    /** La DIAN aún no termina de habilitar a la empresa con Alegra. */
    private static final String NOT_ENABLED_YET = "AEP9009";
    /** El NIT no es de ninguna empresa creada con el token de la plataforma. */
    private static final String UNKNOWN_NIT = "AEP9008";

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompanyEnvelope(AlegraCompany company) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResolutionsEnvelope(List<Resolution> resolutions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Resolution(String resolutionNumber, String prefix, Long minNumber, Long maxNumber,
                              String startDate, String endDate, String technicalKey) {}

    private final AlegraClientHelper helper;
    private final AlegraAccessResolver accessResolver;

    public AlegraOnboardingClient(AlegraClientHelper helper, AlegraAccessResolver accessResolver) {
        this.helper = helper;
        this.accessResolver = accessResolver;
    }

    @Override
    public String createCompany(BillingEnvironment environment, CompanyProfileData profile) {
        AlegraAccess access = accessResolver.platformAccess(environment);
        Map<?, ?> response;
        try {
            response = helper.authorizedPost("/companies", access)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(companyBody(profile))
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            throw helper.translate("No se pudo crear la empresa en Alegra", ex);
        }
        String companyId = companyIdOf(response);
        if (companyId == null) {
            throw new AlegraApiException(0, "Alegra creó la empresa pero no devolvió su identificador", null);
        }
        return companyId;
    }

    @Override
    public void updateCompany(BillingEnvironment environment, String companyId, CompanyProfileData profile) {
        AlegraAccess access = accessResolver.platformAccess(environment);
        try {
            helper.authorizedPatch("/companies/" + required(companyId, "company.id"), access)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(companyBody(profile))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw helper.translate("No se pudo actualizar la empresa en Alegra", ex);
        }
    }

    @Override
    public void startDianEnablement(BillingEnvironment environment, String companyId, String testSetId) {
        AlegraAccess access = accessResolver.platformAccess(environment);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "invoices");
        body.put("governmentId", required(testSetId, "TestSetId"));
        body.put("company", Map.of("id", required(companyId, "company.id")));
        try {
            helper.authorizedPost("/test-sets", access)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            throw helper.translate("No se pudo iniciar la habilitación ante la DIAN en Alegra", ex);
        }
    }

    @Override
    public DianEnablementStatus readEnablement(BillingEnvironment environment, String companyId) {
        AlegraAccess access = accessResolver.platformAccess(environment);
        CompanyEnvelope envelope;
        try {
            envelope = helper.authorizedGet("/companies/" + required(companyId, "company.id"), access)
                    .retrieve()
                    .body(CompanyEnvelope.class);
        } catch (Exception ex) {
            throw helper.translate("No se pudo consultar la habilitación ante la DIAN en Alegra", ex);
        }
        if (envelope == null || envelope.company() == null) {
            throw new AlegraApiException(0, "Alegra devolvió una respuesta vacía al consultar la empresa", null);
        }
        Map<String, String> governmentStatus = envelope.company().governmentStatus();
        return DianEnablementStatus.of(governmentStatus == null ? null : governmentStatus.get("invoices"));
    }

    @Override
    public List<DianResolutionData> listResolutions(BillingEnvironment environment, String nit) {
        AlegraAccess access = accessResolver.platformAccess(environment);
        String identification = required(nit, "NIT");
        ResolutionsEnvelope envelope;
        try {
            envelope = helper.authorizedGet("/resolutions/" + identification, access)
                    .retrieve()
                    .body(ResolutionsEnvelope.class);
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            if (ex.getStatusCode().value() == 404 && body != null && body.contains(NOT_ENABLED_YET)) {
                throw new BillingNotEnabledException("La DIAN todavía no habilita a esta empresa con Alegra");
            }
            if (ex.getStatusCode().value() == 404 && body != null && body.contains(UNKNOWN_NIT)) {
                throw new BillingNotEnabledException("El NIT " + identification
                        + " no corresponde a ninguna empresa creada en Alegra por la plataforma");
            }
            throw helper.translate("No se pudieron consultar las resoluciones de la DIAN en Alegra", ex);
        } catch (Exception ex) {
            throw helper.translate("No se pudieron consultar las resoluciones de la DIAN en Alegra", ex);
        }
        if (envelope == null || envelope.resolutions() == null) {
            return List.of();
        }
        return envelope.resolutions().stream()
                .filter(Objects::nonNull)
                .map(r -> new DianResolutionData(r.resolutionNumber(), r.prefix(), r.minNumber(), r.maxNumber(),
                        parseDate(r.startDate()), parseDate(r.endDate()), r.technicalKey()))
                .toList();
    }

    @Override
    public boolean isPlatformReady(BillingEnvironment environment) {
        return accessResolver.hasPlatformToken(environment);
    }

    /** Cuerpo de {@code POST/PATCH /companies}. Los campos opcionales vacíos no se envían. */
    private static Map<String, Object> companyBody(CompanyProfileData profile) {
        Objects.requireNonNull(profile, "Los datos fiscales de la empresa son obligatorios");
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, "name", profile.name());
        putIfPresent(body, "tradeName", profile.tradeName());
        putIfPresent(body, "identification", profile.identification());
        putIfPresent(body, "dv", profile.dv());
        body.put("useAlegraCertificate", true);
        body.put("type", "associated");
        putIfPresent(body, "organizationType", profile.organizationType());
        putIfPresent(body, "identificationType", profile.identificationType());
        putIfPresent(body, "regimeCode", profile.regimeCode());
        if (profile.economicActivities() != null && !profile.economicActivities().isEmpty()) {
            body.put("economicActivities", List.copyOf(profile.economicActivities()));
        }
        putIfPresent(body, "email", profile.email());
        putIfPresent(body, "phone", profile.phone());
        if (hasText(profile.address()) || hasText(profile.cityCode()) || hasText(profile.departmentCode())) {
            Map<String, Object> address = new LinkedHashMap<>();
            putIfPresent(address, "address", profile.address());
            putIfPresent(address, "city", profile.cityCode());
            putIfPresent(address, "department", profile.departmentCode());
            address.put("country", "CO");
            body.put("address", address);
        }
        body.put("notificationByEmail", Map.of("enabled", true));
        return body;
    }

    /** Alegra responde {@code {"company":{"id":...}}}; por si acaso también se acepta un {@code id} en la raíz. */
    private static String companyIdOf(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        if (response.get("company") instanceof Map<?, ?> company && company.get("id") != null) {
            return text(company.get("id"));
        }
        return text(response.get("id"));
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        if (value instanceof String s) {
            if (!s.isBlank()) {
                body.put(key, s.trim());
            }
        } else if (value != null) {
            body.put(key, value);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String required(String value, String label) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("Falta el " + label + " para la llamada a Alegra");
        }
        return value.trim();
    }

    /** Fechas {@code yyyy-MM-dd}; si Alegra agrega la hora, se toma solo la fecha. Inválida o vacía: {@code null}. */
    private static LocalDate parseDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDate.parse(trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
