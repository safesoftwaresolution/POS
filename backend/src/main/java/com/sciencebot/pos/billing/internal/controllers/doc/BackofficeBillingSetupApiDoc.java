package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.BillingSetupDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "📄 Facturación Electrónica - Habilitación (Back Office)",
        description = "Credenciales, entorno y etapa de habilitación de facturación electrónica por local (SUPER_ADMIN)")
public interface BackofficeBillingSetupApiDoc {

    @Operation(summary = "Avance del asistente de habilitación de un local",
            description = """
                    El mismo avance que ve el dueño en su asistente (pasos, datos fiscales y rangos de facturas), para
                    seguirlo desde el back office. Solo SUPER_ADMIN. Igual que el GET del local, si el local ya cumple
                    todo queda activo (actor system).
                    """)
    ResponseEntity<BillingSetupDto> getStoreSetup(Long storeId);
}
