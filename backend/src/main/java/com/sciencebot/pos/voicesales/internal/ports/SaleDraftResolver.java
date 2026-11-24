package com.sciencebot.pos.voicesales.internal.ports;

import com.sciencebot.pos.voicesales.VoiceSalePreviewDto;

@FunctionalInterface
public interface SaleDraftResolver {
    VoiceSalePreviewDto resolve(SaleIntent intent);
}
