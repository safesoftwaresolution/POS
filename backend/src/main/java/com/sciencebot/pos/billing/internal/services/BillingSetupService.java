package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.BillingSetupDto;
import com.sciencebot.pos.billing.dto.FiscalProfileRequest;
import com.sciencebot.pos.billing.dto.NumberingImportResultDto;

/**
 * Asistente de habilitación de la facturación electrónica en autoservicio: datos fiscales (empresa en producción),
 * habilitación ante la DIAN con el set de pruebas, importación de la numeración y activación automática.
 * Ninguna llamada al proveedor se hace dentro de una transacción.
 */
public interface BillingSetupService {

    /**
     * Avance del local, paso a paso. Si el local está en configuración y ya tiene la habilitación de la DIAN y una
     * numeración de facturas utilizable, lo activa (actor {@code system}).
     */
    BillingSetupDto getSetup(Long storeId);

    /**
     * Guarda los datos del RUT, deja al local en configuración y en producción, y crea (o actualiza) su empresa en
     * el proveedor. Si la plataforma no tiene acceso a producción o el proveedor rechaza los datos, los datos quedan
     * guardados y el paso FISCAL_DATA vuelve con el error (no lanza excepción).
     */
    BillingSetupDto saveFiscalProfile(Long storeId, FiscalProfileRequest request, String actor);

    /** Envía el TestSetId de la DIAN para habilitar la empresa del local. Exige los datos fiscales registrados. */
    BillingSetupDto startDianEnablement(Long storeId, String testSetId, String actor);

    /**
     * Importa como rangos de facturas las resoluciones que la DIAN tiene para el NIT del local y, si el local ya
     * tiene un rango de facturas activo, crea los rangos de notas crédito y débito que falten. Exige la habilitación
     * de la DIAN y que {@code storeId} sea el local de la solicitud en curso.
     */
    NumberingImportResultDto importNumbering(Long storeId, String actor);
}
