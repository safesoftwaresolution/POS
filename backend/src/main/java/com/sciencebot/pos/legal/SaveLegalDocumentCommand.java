package com.sciencebot.pos.legal;

public record SaveLegalDocumentCommand(String slug, String title, String content, String version) {}