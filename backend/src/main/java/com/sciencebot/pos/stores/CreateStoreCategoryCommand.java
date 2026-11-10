package com.sciencebot.pos.stores;

import java.util.List;

/**
 * {@code templates}, {@code template} and {@code theme} are optional. {@code template} is the legacy single key that a
 * cached frontend may still send; {@code templates} wins when both are present. Nothing sent means RETAIL / DEFAULT on
 * create and "keep the current one" on update.
 */
public record CreateStoreCategoryCommand(String name, String description, String template, String theme, List<String> templates) {

    public CreateStoreCategoryCommand(String name, String description) {
        this(name, description, null, null, null);
    }

    public CreateStoreCategoryCommand(String name, String description, String template) {
        this(name, description, template, null, null);
    }

    public CreateStoreCategoryCommand(String name, String description, String template, String theme) {
        this(name, description, template, theme, null);
    }

    /** The template keys that were sent: the list if there is one, otherwise the legacy single key; empty if none. */
    public List<String> templateKeys() {
        if (templates != null && !templates.isEmpty()) return templates;
        if (template != null && !template.isBlank()) return List.of(template);
        return List.of();
    }
}
