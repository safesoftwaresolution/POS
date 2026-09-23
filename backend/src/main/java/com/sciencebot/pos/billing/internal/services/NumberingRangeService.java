package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.dto.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.dto.NumberingRangeDto;

import java.util.List;

/**
 * Servicio interno para la gestión y sincronización de Rangos de Numeración.
 */
public interface NumberingRangeService {

    List<NumberingRangeDto> queryDianNumberingRanges();

    List<NumberingRangeDto> listNumberingRanges();

    NumberingRangeDto getNumberingRange(Long numberingRangeId);

    NumberingRangeDto createNumberingRange(CreateNumberingRangeRequest request);

    void deleteNumberingRange(Long numberingRangeId);

    NumberingRangeDto toggleNumberingRangeStatus(Long numberingRangeId);

    NumberingRangeDto updateNumberingRangeCurrent(Long numberingRangeId, Long current);

    /**
     * Fija el rango con el que el local en contexto factura en su entorno activo
     * (sandbox o producción). El rango debe existir en el proveedor y estar activo.
     */
    NumberingRangeDto setDefaultNumberingRange(Long numberingRangeId);
}
