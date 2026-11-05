package com.sciencebot.pos.stores;

/** {@code template} is optional: blank keeps the current one on update and means RETAIL on create. */
public record CreateStoreCategoryCommand(String name, String description, String template) {

    public CreateStoreCategoryCommand(String name, String description) {
        this(name, description, null);
    }
}
