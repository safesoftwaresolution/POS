package com.sciencebot.pos.reports.internal.services;

import com.sciencebot.pos.reports.ExpiringProductDto;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Dibuja el informe de vencimientos en un PDF A4 horizontal; no consulta datos ni conoce el tenant. */
class ExpiringReportPdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
    private static final Font HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final String[] HEADERS = {"Producto", "Código", "Lote", "Vence", "Estado", "Cant. del lote", "Stock total"};
    private static final float[] WIDTHS = {4.2f, 1.6f, 1.6f, 1.5f, 2.2f, 1.4f, 1.3f};

    byte[] render(List<ExpiringProductDto> rows, int windowDays, LocalDate today) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Productos vencidos y próximos a vencer", TITLE));
            long expired = rows.stream().filter(r -> r.daysToExpire() < 0).count();
            Paragraph subtitle = new Paragraph(
                    "Generado el " + today.format(DATE) + " · ventana de " + windowDays + " días · "
                            + expired + " lote(s) vencido(s), " + (rows.size() - expired) + " por vencer", SUBTITLE);
            subtitle.setSpacingAfter(12);
            document.add(subtitle);

            if (rows.isEmpty()) {
                document.add(new Paragraph("Ningún lote está vencido ni vence en los próximos " + windowDays + " días.", BODY));
            } else {
                document.add(table(rows));
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de vencimientos", e);
        }
        return out.toByteArray();
    }

    private PdfPTable table(List<ExpiringProductDto> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(HEADERS.length);
        table.setWidthPercentage(100);
        table.setWidths(WIDTHS);
        table.setHeaderRows(1);
        for (String header : HEADERS) {
            PdfPCell cell = new PdfPCell(new Phrase(header, HEADER));
            cell.setBackgroundColor(new Color(0x33, 0x41, 0x55));
            cell.setPadding(5);
            table.addCell(cell);
        }
        for (ExpiringProductDto row : rows) {
            Color tint = row.daysToExpire() < 0 ? new Color(0xFE, 0xE2, 0xE2)
                    : row.daysToExpire() <= 7 ? new Color(0xFE, 0xF3, 0xC7) : null;
            add(table, row.productName(), tint, Element.ALIGN_LEFT, false);
            add(table, row.internalCode(), tint, Element.ALIGN_LEFT, false);
            add(table, row.lotCode(), tint, Element.ALIGN_LEFT, false);
            add(table, row.expirationDate().format(DATE), tint, Element.ALIGN_LEFT, false);
            add(table, statusLabel(row.daysToExpire()), tint, Element.ALIGN_LEFT, true);
            add(table, String.valueOf(row.lotRemaining()), tint, Element.ALIGN_RIGHT, false);
            add(table, String.valueOf(row.quantityAvailable()), tint, Element.ALIGN_RIGHT, false);
        }
        return table;
    }

    private void add(PdfPTable table, String text, Color background, int align, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, bold ? BODY_BOLD : BODY));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        if (background != null) cell.setBackgroundColor(background);
        table.addCell(cell);
    }

    static String statusLabel(long days) {
        if (days < -1) return "Vencido hace " + (-days) + " días";
        if (days == -1) return "Venció ayer";
        if (days == 0) return "Vence hoy";
        if (days == 1) return "Vence mañana";
        return "Vence en " + days + " días";
    }
}
