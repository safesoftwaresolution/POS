package com.sciencebot.pos.billing.internal.adapters.dto;

import java.time.LocalDate;

/** Resolución de numeración que la DIAN tiene registrada para un NIT (lo que se importa como rango local). */
public record DianResolutionData(
        String resolutionNumber,
        String prefix,
        Long minNumber,
        Long maxNumber,
        LocalDate startDate,
        LocalDate endDate,
        String technicalKey
) {}
