package com.sciencebot.pos.stores;

import java.util.List;

/** {@code template} is the main one (APPOINTMENTS &gt; FOOD &gt; RETAIL); {@code templates} is the whole set. */
public record StoreCategoryDto(Long id, String name, String description, boolean active, String template, String theme,
                               List<String> templates) {}
