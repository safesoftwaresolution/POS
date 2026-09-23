package com.sciencebot.pos.billing.internal.controllers.doc;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;
import com.sciencebot.pos.billing.dto.UpdateNumberingRangeCurrentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "📄 Facturación Electrónica - Rangos de Numeración", description = "Administración y sincronización de resoluciones y rangos DIAN en Factus")
public interface BillingNumberingRangeApiDoc {

    @Operation(
            summary = "Consultar rangos de numeración asociados ante la DIAN",
            description = "Consulta en tiempo real ante la DIAN los rangos y resoluciones autorizados y enlazados con el software."
    )
    ResponseEntity<List<NumberingRangeDto>> getDianNumberingRanges();

    @Operation(
            summary = "Listar rangos de numeración registrados en Factus",
            description = "Lista todos los rangos de numeración registrados y activos en Factus."
    )
    ResponseEntity<List<NumberingRangeDto>> listNumberingRanges();

    @Operation(
            summary = "Consultar detalle de un rango de numeración",
            description = "Obtiene los datos detallados de un rango de numeración por su ID."
    )
    ResponseEntity<NumberingRangeDto> getNumberingRange(
            @Parameter(description = "ID del rango de numeración", example = "14", required = true) Long id
    );

    @Operation(
            summary = "Registrar y activar un nuevo rango de numeración en Factus",
            description = "Registra una resolución DIAN en Factus para habilitarla en la emisión de facturas."
    )
    ResponseEntity<NumberingRangeDto> createNumberingRange(CreateNumberingRangeRequest request);

    @Operation(
            summary = "Eliminar rango de numeración en Factus",
            description = "Elimina un rango de numeración. Si ya tuvo facturas, Factus lo deja marcado como eliminado en lugar de borrarlo."
    )
    ResponseEntity<Void> deleteNumberingRange(
            @Parameter(description = "ID del rango de numeración a eliminar", example = "14", required = true) Long id
    );

    @Operation(
            summary = "Cambiar estado (activo/inactivo) de un rango de numeración",
            description = "Alterna el estado de activación de un rango de numeración en Factus y devuelve el rango actualizado."
    )
    ResponseEntity<NumberingRangeDto> toggleNumberingRangeStatus(
            @Parameter(description = "ID del rango de numeración", example = "14", required = true) Long id
    );

    @Operation(
            summary = "Actualizar el consecutivo actual de un rango de numeración",
            description = "Cambia el próximo número a usar. Útil cuando el local ya venía facturando con otro software."
    )
    ResponseEntity<NumberingRangeDto> updateNumberingRangeCurrent(
            @Parameter(description = "ID del rango de numeración", example = "14", required = true) Long id,
            UpdateNumberingRangeCurrentRequest request
    );

    @Operation(
            summary = "Usar un rango de numeración para facturar",
            description = "Deja el rango como el rango activo del local en su entorno actual (sandbox o producción). "
                    + "Las siguientes facturas se emiten con este rango. El rango debe existir en Factus y estar activo."
    )
    ResponseEntity<NumberingRangeDto> setDefaultNumberingRange(
            @Parameter(description = "ID del rango de numeración", example = "14", required = true) Long id
    );
}
