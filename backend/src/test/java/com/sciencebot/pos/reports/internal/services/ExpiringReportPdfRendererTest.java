package com.sciencebot.pos.reports.internal.services;

import com.sciencebot.pos.reports.ExpiringProductDto;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiringReportPdfRendererTest {

    private final ExpiringReportPdfRenderer renderer = new ExpiringReportPdfRenderer();
    private final LocalDate today = LocalDate.of(2026, 10, 28);

    @Test
    void render_withRows_producesAPdf() {
        List<ExpiringProductDto> rows = List.of(
                new ExpiringProductDto(1L, "Leche entera 1L", "P-1", 7L, "L-2210", today.minusDays(3), -3, 4, 10, "EXPIRED"),
                new ExpiringProductDto(2L, "Yogurt", "P-2", 8L, null, today.plusDays(5), 5, 24, 60, "EXPIRING"));

        byte[] pdf = renderer.render(rows, 30, today);

        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
        assertTrue(pdf.length > 1000);
    }

    @Test
    void render_withoutRows_stillProducesAPdf() {
        byte[] pdf = renderer.render(List.of(), 30, today);

        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
    }

    @Test
    void statusLabel_matchesTheFrontendWording() {
        assertEquals("Vencido hace 3 días", ExpiringReportPdfRenderer.statusLabel(-3));
        assertEquals("Venció ayer", ExpiringReportPdfRenderer.statusLabel(-1));
        assertEquals("Vence hoy", ExpiringReportPdfRenderer.statusLabel(0));
        assertEquals("Vence mañana", ExpiringReportPdfRenderer.statusLabel(1));
        assertEquals("Vence en 12 días", ExpiringReportPdfRenderer.statusLabel(12));
    }
}
