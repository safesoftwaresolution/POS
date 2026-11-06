package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.BillingSetupDto;
import com.sciencebot.pos.billing.dto.DianEnablementRequest;
import com.sciencebot.pos.billing.dto.FiscalProfileRequest;
import com.sciencebot.pos.billing.dto.NumberingImportResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "📄 Facturación Electrónica - Asistente", description = "Habilitación en autoservicio de la facturación electrónica del local autenticado (producción)")
public interface BillingSetupApiDoc {

    @Operation(summary = "Avance de la habilitación de mi local",
            description = """
                    Pasos en orden (FISCAL_DATA, DIAN_ENABLEMENT, NUMBERING, READY) con su estado (DONE, CURRENT,
                    PENDING, ERROR) y un mensaje sencillo; los datos fiscales guardados o una sugerencia tomada del
                    local; si la plataforma ya puede habilitar en producción y cuántos rangos de facturas hay. Solo
                    consulta la DIAN si el local está en configuración. Si ya cumple todo, activa el local.
                    """)
    ResponseEntity<BillingSetupDto> getSetup();

    @Operation(summary = "Guardar los datos fiscales (RUT) y registrar la empresa",
            description = """
                    Guarda los datos, deja el local en configuración y en producción, y crea (o actualiza) su
                    empresa en el proveedor. Si la plataforma aún no puede habilitar en producción o el proveedor
                    rechaza los datos, responde 200 con el paso FISCAL_DATA en ERROR y los datos quedan guardados.
                    """)
    ResponseEntity<BillingSetupDto> saveFiscalProfile(FiscalProfileRequest request, Authentication authentication);

    @Operation(summary = "Enviar el set de pruebas de la DIAN",
            description = "Inicia la habilitación como facturador electrónico con el TestSetId que entregó la DIAN. "
                    + "Exige los datos fiscales registrados (si no, 409).")
    ResponseEntity<BillingSetupDto> startDianEnablement(DianEnablementRequest request, Authentication authentication);

    @Operation(summary = "Importar la numeración autorizada por la DIAN",
            description = """
                    Registra como rangos de facturas las resoluciones vigentes que la DIAN tiene para el NIT (las
                    guardas de rangos aplican: un solo rango activo por documento) y crea los rangos de notas
                    crédito (NC) y débito (ND) que falten. Exige la habilitación de la DIAN (si no, 409).
                    """)
    ResponseEntity<NumberingImportResultDto> importNumbering(Authentication authentication);
}
