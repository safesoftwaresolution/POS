package com.sciencebot.pos.billing.internal.adapters;

import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.internal.adapters.dto.CompanyProfileData;
import com.sciencebot.pos.billing.internal.adapters.dto.DianEnablementStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.DianResolutionData;

import java.util.List;

/**
 * Alta de un local ante el proveedor de facturación electrónica: crear y actualizar su empresa, iniciar la
 * habilitación ante la DIAN con el set de pruebas, leer en qué va esa habilitación e importar sus resoluciones de
 * numeración. Usa el acceso de plataforma del entorno (no hace falta que el local ya tenga empresa). La
 * implementación la elige {@code billing.provider} ({@code alegra} o {@code mock}).
 */
public interface BillingOnboardingProvider {

    /** Crea la empresa del local en el proveedor y devuelve su identificador (company.id). */
    String createCompany(BillingEnvironment environment, CompanyProfileData profile);

    /** Actualiza los datos fiscales de una empresa ya creada. */
    void updateCompany(BillingEnvironment environment, String companyId, CompanyProfileData profile);

    /** Inicia la habilitación como facturador electrónico con el TestSetId que la DIAN le dio al dueño. */
    void startDianEnablement(BillingEnvironment environment, String companyId, String testSetId);

    /** En qué va la habilitación de facturas de la empresa ante la DIAN. */
    DianEnablementStatus readEnablement(BillingEnvironment environment, String companyId);

    /** Resoluciones de numeración que la DIAN tiene registradas para el NIT. */
    List<DianResolutionData> listResolutions(BillingEnvironment environment, String nit);

    /** {@code true} si la plataforma tiene credenciales del proveedor para ese entorno. */
    boolean isPlatformReady(BillingEnvironment environment);
}
