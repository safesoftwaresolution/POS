package com.sciencebot.pos.billing.internal.mappers;

import com.sciencebot.pos.billing.dto.FiscalProfileDto;
import com.sciencebot.pos.billing.dto.FiscalProfileRequest;
import com.sciencebot.pos.billing.internal.adapters.dto.CompanyProfileData;
import com.sciencebot.pos.billing.internal.entities.BillingCompanyProfile;
import com.sciencebot.pos.billing.internal.services.DianCheckDigit;
import com.sciencebot.pos.stores.StoreDto;
import org.springframework.stereotype.Component;

import java.util.List;

/** Datos fiscales del local: formulario → entidad, entidad → respuesta / empresa del proveedor, local → sugerencia. */
@Component
public class BillingSetupMapper {

    /** Copia el formulario sobre el perfil (sin espacios sobrantes; los opcionales vacíos quedan en null). */
    public void apply(FiscalProfileRequest request, BillingCompanyProfile profile) {
        profile.setLegalName(request.legalName().trim());
        profile.setTradeName(optional(request.tradeName()));
        profile.setNit(request.nit().trim());
        profile.setDv(request.dv().trim());
        profile.setIdentificationType(BillingCompanyProfile.NIT_IDENTIFICATION_TYPE);
        profile.setOrganizationType(request.organizationType());
        profile.setRegimeCode(request.regimeCode().trim());
        profile.setEconomicActivity(optional(request.economicActivity()));
        profile.setEmail(request.email().trim());
        profile.setPhone(optional(request.phone()));
        profile.setAddress(request.address().trim());
        profile.setCityCode(request.cityCode().trim());
        profile.setDepartmentCode(request.departmentCode().trim());
    }

    public FiscalProfileDto toDto(BillingCompanyProfile profile) {
        return new FiscalProfileDto(profile.getLegalName(), profile.getTradeName(), profile.getNit(), profile.getDv(),
                profile.getOrganizationType(), profile.getRegimeCode(), profile.getEconomicActivity(),
                profile.getEmail(), profile.getPhone(), profile.getAddress(), profile.getCityCode(),
                profile.getDepartmentCode(), true);
    }

    /**
     * Sugerencia para precargar el formulario con los datos del local. El NIT del local puede venir como
     * {@code 900123456-8}: se separa el DV, y si no lo trae se calcula.
     */
    public FiscalProfileDto suggestion(StoreDto store) {
        if (store == null) {
            return emptySuggestion();
        }
        String nit = null;
        String dv = null;
        if (store.taxId() != null && !store.taxId().isBlank()) {
            String[] parts = store.taxId().split("-", 2);
            String digits = parts[0].replaceAll("\\D", "");
            if (!digits.isEmpty() && digits.length() <= 15) {
                nit = digits;
                String given = parts.length > 1 ? parts[1].trim() : "";
                dv = given.matches("\\d") ? given : DianCheckDigit.of(digits);
            }
        }
        return new FiscalProfileDto(store.name(), null, nit, dv, null, null, null, store.email(), store.phone(),
                store.address(), null, null, false);
    }

    public FiscalProfileDto emptySuggestion() {
        return new FiscalProfileDto(null, null, null, null, null, null, null, null, null, null, null, null, false);
    }

    /** Lo que se envía al proveedor para crear o actualizar la empresa del local. */
    public CompanyProfileData toCompanyData(BillingCompanyProfile profile) {
        List<String> activities = profile.getEconomicActivity() != null ? List.of(profile.getEconomicActivity()) : List.of();
        return new CompanyProfileData(profile.getLegalName(), profile.getTradeName(), profile.getNit(), profile.getDv(),
                profile.getOrganizationType(), profile.getIdentificationType(), profile.getRegimeCode(), activities,
                profile.getEmail(), profile.getPhone(), profile.getAddress(), profile.getCityCode(),
                profile.getDepartmentCode());
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
