package com.sciencebot.pos.billing.internal.adapters;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;

import java.util.List;

/**
 * Puerto de salida (SPI) para administración y sincronización de Rangos de Numeración ante la DIAN y el proveedor.
 * Todas las operaciones se hacen con las credenciales del local de la solicitud en curso y lanzan
 * excepción si el proveedor las rechaza (nunca devuelven "vacío" para esconder un error).
 */
public interface NumberingRangeProvider {

    /**
     * Identificador del proveedor (ej. "factus", "mock").
     */
    String getProviderName();

    /**
     * Consulta los rangos de numeración asociados ante la DIAN (consulta en vivo).
     */
    default List<NumberingRangeDto> queryDianNumberingRanges() {
        throw new UnsupportedOperationException("queryDianNumberingRanges no soportado por " + getProviderName());
    }

    /**
     * Lista los rangos de numeración registrados en el proveedor.
     */
    default List<NumberingRangeDto> listNumberingRanges() {
        throw new UnsupportedOperationException("listNumberingRanges no soportado por " + getProviderName());
    }

    /**
     * Obtiene el detalle de un rango de numeración por su ID, o {@code null} si no existe.
     */
    default NumberingRangeDto getNumberingRange(Long numberingRangeId) {
        throw new UnsupportedOperationException("getNumberingRange no soportado por " + getProviderName());
    }

    /**
     * Registra y activa un rango de numeración en el proveedor.
     */
    default NumberingRangeDto createNumberingRange(CreateNumberingRangeRequest request) {
        throw new UnsupportedOperationException("createNumberingRange no soportado por " + getProviderName());
    }

    /**
     * Elimina un rango de numeración en el proveedor. Si ya tuvo documentos, el proveedor lo
     * deja marcado como eliminado en lugar de borrarlo.
     */
    default void deleteNumberingRange(Long numberingRangeId) {
        throw new UnsupportedOperationException("deleteNumberingRange no soportado por " + getProviderName());
    }

    /**
     * Alterna el estado (activo/inactivo) de un rango y devuelve el rango ya actualizado.
     */
    default NumberingRangeDto toggleNumberingRangeStatus(Long numberingRangeId) {
        throw new UnsupportedOperationException("toggleNumberingRangeStatus no soportado por " + getProviderName());
    }

    /**
     * Cambia el consecutivo actual (el próximo número a usar) y devuelve el rango actualizado.
     */
    default NumberingRangeDto updateNumberingRangeCurrent(Long numberingRangeId, Long current) {
        throw new UnsupportedOperationException("updateNumberingRangeCurrent no soportado por " + getProviderName());
    }
}
