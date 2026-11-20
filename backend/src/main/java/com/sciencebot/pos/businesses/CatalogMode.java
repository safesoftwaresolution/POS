package com.sciencebot.pos.businesses;

/** Whether branches keep their own catalog. {@code SHARED} is reserved for phase 2 and is not accepted yet. */
public enum CatalogMode {
    INDEPENDENT,
    SHARED
}
