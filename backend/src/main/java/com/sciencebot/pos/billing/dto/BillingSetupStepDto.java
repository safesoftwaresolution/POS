package com.sciencebot.pos.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un paso del asistente de facturación electrónica, con un mensaje en lenguaje sencillo. */
@Schema(description = "Paso del asistente de habilitación")
public record BillingSetupStepDto(
        @Schema(description = "Paso", example = "FISCAL_DATA")
        Key key,
        @Schema(description = "En qué va el paso", example = "CURRENT")
        State state,
        @Schema(description = "Qué significa o qué hacer ahora", example = "Ingresa los datos de tu RUT.")
        String message
) {

    /** Pasos, en el orden en que se recorren. */
    public enum Key {
        /** Datos del RUT y empresa creada en el proveedor (producción). */
        FISCAL_DATA,
        /** Habilitación como facturador electrónico ante la DIAN (set de pruebas). */
        DIAN_ENABLEMENT,
        /** Numeración de facturas vigente (resolución de la DIAN). */
        NUMBERING,
        /** Facturación activa. */
        READY
    }

    public enum State {
        DONE,
        /** El paso en el que va el local. */
        CURRENT,
        /** Aún no se puede hacer: falta un paso anterior. */
        PENDING,
        /** Algo falló; el mensaje dice qué. */
        ERROR
    }
}
