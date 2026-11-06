package com.sciencebot.pos.billing.internal.adapters.dto;

import java.util.List;

/**
 * Datos fiscales (del RUT) con los que se crea o actualiza la empresa del local en el proveedor.
 *
 * @param organizationType   código DIAN: 1 persona jurídica, 2 persona natural
 * @param identificationType código DIAN del documento (31 = NIT)
 * @param regimeCode         responsabilidades fiscales separadas por {@code ;} (p. ej. {@code R-99-PN})
 * @param economicActivities códigos CIIU de 4 dígitos
 * @param cityCode           código DANE del municipio (5 dígitos)
 * @param departmentCode     código DANE del departamento (2 dígitos)
 */
public record CompanyProfileData(
        String name,
        String tradeName,
        String identification,
        String dv,
        Integer organizationType,
        String identificationType,
        String regimeCode,
        List<String> economicActivities,
        String email,
        String phone,
        String address,
        String cityCode,
        String departmentCode
) {}
