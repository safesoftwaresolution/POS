package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.TransitionBillingStatusRequest;
import com.sciencebot.pos.billing.dto.UpdateBillingCredentialsCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

@Tag(name = "📄 Facturación Electrónica - Habilitación (Back Office)",
        description = "Credenciales, entorno y etapa de habilitación de facturación electrónica por local (SUPER_ADMIN)")
public interface StoreBillingConfigApiDoc {

    @Operation(summary = "Locales en una etapa del proceso de habilitación",
            description = "Cola de revisión: por ejemplo, ?status=DOCUMENTS_SUBMITTED para ver quién espera aprobación.")
    ResponseEntity<List<BillingActivationDto>> listByStatus(BillingActivationStatus status);

    @Operation(summary = "Estado de facturación electrónica de un local",
            description = "Nunca incluye secretos: solo indica si cada entorno tiene credenciales completas.")
    ResponseEntity<BillingActivationDto> get(Long storeId);

    @Operation(summary = "Cargar las credenciales que Factus entregó para un local",
            description = "Un client_secret o password vacío conserva el valor ya guardado para ese entorno. "
                    + "El rango de numeración lo elige el administrador del local; numberingRangeId es opcional.")
    ResponseEntity<BillingActivationDto> updateCredentials(
            Long storeId,
            @Parameter(description = "Entorno que se está configurando", example = "SANDBOX") BillingEnvironment environment,
            UpdateBillingCredentialsCommand command,
            Authentication authentication
    );

    @Operation(summary = "Probar las credenciales guardadas de un entorno contra Factus",
            description = "Pide un token real. No activa el local; el resultado dice si Factus aceptó las credenciales.")
    ResponseEntity<BillingConnectionTestResult> testConnection(
            Long storeId,
            BillingEnvironment environment
    );

    @Operation(summary = "Cambiar el entorno con el que factura el local",
            description = "Exige credenciales completas del entorno destino cuando el local ya está ACTIVE.")
    ResponseEntity<BillingActivationDto> switchEnvironment(
            Long storeId,
            BillingEnvironment environment,
            Authentication authentication
    );

    @Operation(summary = "Cambiar la etapa del proceso de habilitación",
            description = """
                    Solo se permiten los pasos definidos: DOCUMENTS_SUBMITTED → AWAITING_PROVIDER → ACTIVE,
                    con REJECTED (desde DOCUMENTS_SUBMITTED o AWAITING_PROVIDER) y SUSPENDED (desde ACTIVE)
                    como salidas. REJECTED y SUSPENDED exigen 'reason'. Pasar a ACTIVE exige credenciales
                    completas del entorno activo del local. Notifica al local del cambio.
                    """)
    ResponseEntity<BillingActivationDto> transition(
            Long storeId,
            TransitionBillingStatusRequest request,
            Authentication authentication
    );
}
