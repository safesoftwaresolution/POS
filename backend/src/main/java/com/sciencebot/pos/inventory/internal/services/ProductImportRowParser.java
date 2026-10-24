package com.sciencebot.pos.inventory.internal.services;

import com.sciencebot.pos.inventory.internal.services.ImportRow.ImportCell;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Convierte celdas de la plantilla en valores. Los errores salen como {@link IllegalArgumentException} con un
 * mensaje para el usuario (sin el nombre de la columna: lo antepone quien llama).
 */
final class ProductImportRowParser {

    private static final Pattern PLAIN_NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99"); // numeric(12,2)
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    static final Set<BigDecimal> TAX_RATES = Set.of(new BigDecimal("0.00"), new BigDecimal("5.00"), new BigDecimal("19.00"));

    private ProductImportRowParser() {
    }

    /** Clave para comparar nombres: minúsculas, sin tildes y con los espacios colapsados. */
    static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String noAccents = DIACRITICS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        return noAccents.toLowerCase(Locale.ROOT).replace(' ', ' ').trim().replaceAll("\\s+", " ");
    }

    /** Texto recortado o {@code null} si la celda está vacía. Valida el largo máximo. */
    static String text(ImportCell cell, int maxLength) {
        requireNoFormulaError(cell);
        String value = cell.text();
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("tiene " + trimmed.length() + " caracteres; el máximo es " + maxLength);
        }
        return trimmed;
    }

    /** Precio ≥ 0 con 2 decimales, o {@code null} si la celda está vacía. */
    static BigDecimal price(ImportCell cell) {
        BigDecimal value = decimal(cell);
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("no puede ser negativo");
        }
        if (value.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("es demasiado grande");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Entero ≥ 0, o {@code null} si la celda está vacía. */
    static Integer nonNegativeInteger(ImportCell cell) {
        BigDecimal value = decimal(cell);
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("no puede ser negativo");
        }
        try {
            return value.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("debe ser un número entero (sin decimales)");
        }
    }

    /**
     * IVA: 0, 5 o 19. Acepta "19", "19%" y también 0.19 (lo que guarda Excel si la celda tiene formato de
     * porcentaje).
     */
    static BigDecimal taxRate(ImportCell cell) {
        ImportCell clean = cell.text() != null && cell.number() == null
                ? new ImportCell(cell.text().replace("%", ""), null, cell.formulaError())
                : cell;
        BigDecimal value = decimal(clean);
        if (value == null) {
            return null;
        }
        BigDecimal rate = value.setScale(2, RoundingMode.HALF_UP);
        if (!TAX_RATES.contains(rate) && value.compareTo(BigDecimal.ONE) < 0) {
            rate = value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
        }
        if (!TAX_RATES.contains(rate)) {
            throw new IllegalArgumentException("debe ser 0, 5 o 19");
        }
        return rate;
    }

    /**
     * Número de una celda. Las celdas numéricas se usan tal cual; en las de texto se aceptan "$", espacios y
     * separadores colombianos o ingleses: "12.500", "12.500,50", "12,500.50", "1,5". Un único separador
     * seguido de exactamente 3 dígitos se toma como separador de miles.
     */
    static BigDecimal decimal(ImportCell cell) {
        requireNoFormulaError(cell);
        if (cell.number() != null) {
            return cell.number();
        }
        if (cell.text() == null || cell.text().isBlank()) {
            return null;
        }
        String raw = cell.text().trim();
        String s = raw.replace("$", "").replace("COP", "").replace("cop", "").replaceAll("[\\s\\u00A0]", "");
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            char decimalSeparator = lastDot > lastComma ? '.' : ',';
            char thousandsSeparator = decimalSeparator == '.' ? ',' : '.';
            s = s.replace(String.valueOf(thousandsSeparator), "").replace(decimalSeparator, '.');
        } else if (lastDot >= 0 || lastComma >= 0) {
            char separator = lastDot >= 0 ? '.' : ',';
            int occurrences = s.length() - s.replace(String.valueOf(separator), "").length();
            int digitsAfter = s.length() - s.lastIndexOf(separator) - 1;
            if (occurrences == 1 && digitsAfter != 3) {
                s = s.replace(separator, '.');
            } else {
                s = s.replace(String.valueOf(separator), "");
            }
        }
        if (!PLAIN_NUMBER.matcher(s).matches()) {
            throw new IllegalArgumentException("'" + raw + "' no es un número válido");
        }
        return new BigDecimal(s);
    }

    private static void requireNoFormulaError(ImportCell cell) {
        if (cell.formulaError() != null) {
            throw new IllegalArgumentException("la celda tiene un error de fórmula (" + cell.formulaError() + ")");
        }
    }
}
