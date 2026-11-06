package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingConnectionTestResult;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.TransitionBillingStatusRequest;
import com.sciencebot.pos.billing.dto.UpdateProviderCompanyIdCommand;
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
            description = "Seguimiento: por ejemplo, ?status=IN_SETUP para ver qué locales están configurando su facturación.")
    ResponseEntity<List<BillingActivationDto>> listByStatus(BillingActivationStatus status);

    @Operation(summary = "Estado de facturación electrónica de un local",
            description = "Nunca incluye secretos: solo indica si cada entorno tiene credenciales completas.")
    ResponseEntity<BillingActivationDto> get(Long storeId);

    @Operation(summary = "Cargar el company.id que el proveedor asignó al local en un entorno",
            description = "Alegra identifica a cada local por su company.id (empresa asociada bajo el token de plataforma). "
                    + "Idempotente. La respuesta solo indica si el entorno ya tiene company.id, no su valor.")
    ResponseEntity<BillingActivationDto> updateProviderCompanyId(
            Long storeId,
            BillingEnvironment environment,
            UpdateProviderCompanyIdCommand command,
            Authentication authentication
    );

    @Operation(summary = "Probar las credenciales guardadas de un entorno contra el proveedor",
            description = "Pide un token real. No activa el local; el resultado dice si el proveedor aceptó las credenciales.")
    ResponseEntity<BillingConnectionTestResult> testConnection(
            Long storeId,
            BillingEnvironment environment
    );

    @Operation(summary = "Cambiar el entorno con el que factura el local",
            description = "En cualquier estado, el entorno destino necesita la empresa del local y una numeración de facturas "
                    + "vigente. Si cambia, avisa al local qué entorno quedó activo (en SANDBOX las facturas son de prueba).")
    ResponseEntity<BillingActivationDto> switchEnvironment(
            Long storeId,
            BillingEnvironment environment,
            Authentication authentication
    );

    @Operation(summary = "Cambiar la etapa del proceso de habilitación",
            description = """
                    Solo se permiten los pasos definidos: NOT_REQUESTED → IN_SETUP → ACTIVE ⇄ SUSPENDED,
                    y SUSPENDED también desde IN_SETUP. SUSPENDED exige 'reason'. Pasar a ACTIVE (también desde
                    SUSPENDED) exige, en el entorno del local, su empresa en el proveedor, la habilitación de la
                    DIAN en AUTHORIZED y una numeración de facturas vigente (409 con el motivo). Notifica al local
                    cuando queda ACTIVE o SUSPENDED.
                    """)
    ResponseEntity<BillingActivationDto> transition(
            Long storeId,
            TransitionBillingStatusRequest request,
            Authentication authentication
    );
}
