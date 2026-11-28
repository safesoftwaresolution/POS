package com.sciencebot.pos.voicesales.internal.services;

import org.springframework.http.HttpStatus;

public final class PreviewRejected extends RuntimeException {
    private final HttpStatus status;

    public PreviewRejected(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
