package com.sciencebot.pos.audit;

/** Un campo modificado: su valor antes y después ({@code from} es null al crear). */
public record FieldChange(Object from, Object to) {
}
