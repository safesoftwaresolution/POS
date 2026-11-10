package com.sciencebot.pos.stores;

import java.util.List;

/** Extra templates for one store, on top of its category's ones. An empty list removes them; null is invalid. */
public record UpdateStoreTemplatesCommand(List<String> extraTemplates) {}
