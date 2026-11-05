package com.sciencebot.pos.stores;

/**
 * {@code template} and {@code theme} are optional: blank keeps the current one on update and means RETAIL / DEFAULT on create.
 */
public record CreateStoreCategoryCommand(String name, String description, String template, String theme) {

    public CreateStoreCategoryCommand(String name, String description) {
        this(name, description, null, null);
    }

    public CreateStoreCategoryCommand(String name, String description, String template) {
        this(name, description, template, null);
    }
}
