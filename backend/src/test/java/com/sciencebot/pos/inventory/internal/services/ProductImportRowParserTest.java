package com.sciencebot.pos.inventory.internal.services;

import com.sciencebot.pos.inventory.internal.services.ImportRow.ImportCell;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductImportRowParserTest {

    @Test
    void decimal_acceptsColombianAndEnglishFormatsWrittenAsText() {
        assertDecimal("12500", "12500");
        assertDecimal("12.500", "12500");
        assertDecimal("$ 12.500", "12500");
        assertDecimal("1.250.000", "1250000");
        assertDecimal("12.500,50", "12500.50");
        assertDecimal("12,500.50", "12500.50");
        assertDecimal("1,5", "1.5");
        assertDecimal("3200.75", "3200.75");
    }

    @Test
    void decimal_usesNumericCellsAsIs() {
        // Una celda numérica no pasa por la heurística de separadores: 0.125 sigue siendo 0.125.
        assertEquals(0, new BigDecimal("0.125").compareTo(
                ProductImportRowParser.decimal(new ImportCell("0.125", new BigDecimal("0.125"), null))));
    }

    @Test
    void decimal_rejectsText() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> ProductImportRowParser.decimal(text("doce")));
        assertTrue(ex.getMessage().contains("no es un número válido"));
    }

    @Test
    void price_roundsToTwoDecimalsAndRejectsNegatives() {
        assertEquals(new BigDecimal("10.13"), ProductImportRowParser.price(new ImportCell("10.125", new BigDecimal("10.125"), null)));
        assertEquals(new BigDecimal("10125.00"), ProductImportRowParser.price(text("10,125")), "un separador con 3 dígitos = miles");
        assertThrows(IllegalArgumentException.class, () -> ProductImportRowParser.price(text("-1")));
    }

    @Test
    void nonNegativeInteger_rejectsDecimalsAndNegatives() {
        assertEquals(40, ProductImportRowParser.nonNegativeInteger(new ImportCell("40", new BigDecimal("40.0"), null)));
        assertThrows(IllegalArgumentException.class, () -> ProductImportRowParser.nonNegativeInteger(text("2,5")));
        assertThrows(IllegalArgumentException.class, () -> ProductImportRowParser.nonNegativeInteger(text("-3")));
    }

    @Test
    void taxRate_accepts0_5_19AndPercentFormattedCells() {
        assertEquals(new BigDecimal("19.00"), ProductImportRowParser.taxRate(text("19%")));
        assertEquals(new BigDecimal("5.00"), ProductImportRowParser.taxRate(new ImportCell("0.05", new BigDecimal("0.05"), null)));
        assertEquals(new BigDecimal("0.00"), ProductImportRowParser.taxRate(text("0")));
        assertThrows(IllegalArgumentException.class, () -> ProductImportRowParser.taxRate(text("16")));
    }

    @Test
    void text_trimsAndChecksLength() {
        assertEquals("Arroz", ProductImportRowParser.text(text("  Arroz  "), 10));
        assertNull(ProductImportRowParser.text(text("   "), 10));
        assertThrows(IllegalArgumentException.class, () -> ProductImportRowParser.text(text("demasiado largo"), 5));
    }

    @Test
    void formulaErrorsAreReported() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ProductImportRowParser.price(new ImportCell(null, null, "#DIV/0!")));
        assertTrue(ex.getMessage().contains("#DIV/0!"));
    }

    @Test
    void headersAreMatchedIgnoringCaseAccentsAndTheRequiredMark() {
        assertEquals(ImportColumn.INTERNAL_CODE, ImportColumn.fromHeader("Código interno *"));
        assertEquals(ImportColumn.INTERNAL_CODE, ImportColumn.fromHeader("CODIGO INTERNO"));
        assertEquals(ImportColumn.MIN_STOCK, ImportColumn.fromHeader("stock  mínimo"));
        assertEquals(ImportColumn.SALE_PRICE, ImportColumn.fromHeader("Precio"));
        assertNull(ImportColumn.fromHeader("Columna inventada"));
    }

    private static void assertDecimal(String input, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(ProductImportRowParser.decimal(text(input))), input);
    }

    private static ImportCell text(String value) {
        return new ImportCell(value, null, null);
    }
}
