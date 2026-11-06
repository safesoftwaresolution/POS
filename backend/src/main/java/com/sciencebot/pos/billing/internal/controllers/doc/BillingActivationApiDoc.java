package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.BillingActivationDto;
import com.sciencebot.pos.billing.dto.BillingActivationSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@Tag(name = "📄 Facturación Electrónica - Habilitación", description = "Estado y solicitud de activación de facturación electrónica del local autenticado")
public interface BillingActivationApiDoc {

    @Operation(summary = "Estado de la facturación electrónica de mi local",
            description = "NOT_REQUESTED si nunca se pidió. Nunca incluye credenciales.")
    ResponseEntity<BillingActivationDto> getStatus();

    @Operation(summary = "¿Mi local puede facturar ahora?",
            description = "Para el POS (cualquier rol del local): si la facturación está activa y en qué entorno. "
                    + "En SANDBOX las facturas son de prueba y no tienen validez legal.")
    ResponseEntity<BillingActivationSummaryDto> getSummary();

    @Operation(summary = "Empezar la habilitación de la facturación electrónica",
            description = """
                    Pasa el local de NOT_REQUESTED a IN_SETUP para que configure su facturación (datos
                    fiscales, habilitación ante la DIAN y numeración). No pide documentos. Repetirlo
                    estando en IN_SETUP no cambia nada; un local ACTIVE o SUSPENDED recibe un error.
                    """)
    ResponseEntity<BillingActivationDto> requestActivation(Authentication authentication);
}
