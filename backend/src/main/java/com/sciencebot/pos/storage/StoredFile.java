package com.sciencebot.pos.storage;

import java.time.Instant;

/** Objeto existente en el bucket de R2 (resultado de listar por prefijo). */
public record StoredFile(String key, String url, Instant lastModified) {}
