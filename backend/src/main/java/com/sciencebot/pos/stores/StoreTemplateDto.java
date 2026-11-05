package com.sciencebot.pos.stores;

import java.util.List;

/** What the UI needs to adapt: the main template (legacy), the visual theme and every template the store runs with. */
public record StoreTemplateDto(String template, String theme, List<String> templates) {}
