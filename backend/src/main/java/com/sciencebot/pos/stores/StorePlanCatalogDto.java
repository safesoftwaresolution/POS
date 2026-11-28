package com.sciencebot.pos.stores;

import java.util.List;

/** The plan catalog as seen by a store: its current plan code plus every plan with its limits and prices. */
public record StorePlanCatalogDto(String currentPlanCode, List<PlanDto> plans) {}
