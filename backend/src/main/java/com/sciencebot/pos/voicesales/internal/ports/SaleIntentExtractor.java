package com.sciencebot.pos.voicesales.internal.ports;

@FunctionalInterface
public interface SaleIntentExtractor {
    SaleIntent extract(String transcript);
}
