package com.sciencebot.pos.inventory.internal.services;

import com.sciencebot.pos.inventory.internal.services.ImportRow.ImportCell;
import com.sciencebot.pos.products.ProductDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Escribe la plantilla de importación masiva y lee las filas de un archivo lleno (Apache POI, .xlsx). */
@Component
class ProductImportWorkbook {

    static final String PRODUCTS_SHEET = "Productos";
    static final String INSTRUCTIONS_SHEET = "Instrucciones";
    static final String LISTS_SHEET = "Listas";
    /** Filas de datos por archivo. La validación de datos de la plantilla cubre este mismo rango. */
    static final int MAX_ROWS = 5000;
    private static final String MAX_ROWS_LABEL = "5.000";

    private static final ImportColumn[] COLUMNS = ImportColumn.values();

    // ---------------------------------------------------------------- escritura

    byte[] writeTemplate(List<String> categoryNames, List<ProductDto> products) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            Sheet instructions = workbook.createSheet(INSTRUCTIONS_SHEET);
            Sheet sheet = workbook.createSheet(PRODUCTS_SHEET);
            Sheet lists = workbook.createSheet(LISTS_SHEET);

            writeLists(lists, categoryNames);
            writeProductsSheet(workbook, sheet, styles, categoryNames.size(), products);
            writeInstructions(instructions, styles, categoryNames);

            workbook.setSheetHidden(workbook.getSheetIndex(lists), true);
            int productsIndex = workbook.getSheetIndex(sheet);
            workbook.setActiveSheet(productsIndex);
            workbook.setSelectedTab(productsIndex);
            instructions.setSelected(false);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la plantilla de productos", e);
        }
    }

    private void writeProductsSheet(Workbook workbook, Sheet sheet, Styles styles, int categoryCount,
                                    List<ProductDto> products) {
        CreationHelper factory = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        Row header = sheet.createRow(0);
        for (int i = 0; i < COLUMNS.length; i++) {
            ImportColumn column = COLUMNS[i];
            Cell cell = header.createCell(i);
            cell.setCellValue(column.headerText());
            cell.setCellStyle(column.requiredForNew ? styles.requiredHeader : styles.optionalHeader);

            ClientAnchor anchor = factory.createClientAnchor();
            anchor.setCol1(i);
            anchor.setCol2(i + 3);
            anchor.setRow1(0);
            anchor.setRow2(5);
            Comment comment = drawing.createCellComment(anchor);
            comment.setString(factory.createRichTextString(column.help));
            comment.setAuthor("POS");
            cell.setCellComment(comment);

            sheet.setColumnWidth(i, column.width * 256);
            if (column.kind == ImportColumn.Kind.TEXT) {
                // Formato texto: Excel no convierte los códigos de barras a notación científica ni quita ceros.
                sheet.setDefaultColumnStyle(i, styles.text);
            }
        }
        header.setHeightInPoints(22);
        sheet.createFreezePane(2, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, COLUMNS.length - 1));

        addValidations(sheet, categoryCount);

        int rowIndex = 1;
        for (ProductDto product : products) {
            writeProductRow(sheet.createRow(rowIndex++), product, styles);
        }
    }

    private void writeProductRow(Row row, ProductDto product, Styles styles) {
        Map<ImportColumn, Object> values = new EnumMap<>(ImportColumn.class);
        values.put(ImportColumn.INTERNAL_CODE, product.internalCode());
        values.put(ImportColumn.NAME, product.name());
        values.put(ImportColumn.CATEGORY, product.categoryName());
        values.put(ImportColumn.SUBCATEGORY, product.subcategoryName());
        values.put(ImportColumn.BARCODE, product.barcode());
        values.put(ImportColumn.PURCHASE_PRICE, product.purchasePrice());
        values.put(ImportColumn.SALE_PRICE, product.salePrice());
        values.put(ImportColumn.TAX_RATE, product.taxRate());
        values.put(ImportColumn.STOCK, product.quantityAvailable());
        values.put(ImportColumn.MIN_STOCK, product.minStock());
        values.put(ImportColumn.DESCRIPTION, product.description());
        values.put(ImportColumn.UNSPSC_CODE, product.unspscCode());
        for (int i = 0; i < COLUMNS.length; i++) {
            Object value = values.get(COLUMNS[i]);
            if (value == null) {
                continue;
            }
            Cell cell = row.createCell(i);
            if (value instanceof BigDecimal number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof Integer number) {
                cell.setCellValue(number);
            } else {
                cell.setCellValue(value.toString());
                cell.setCellStyle(styles.text);
            }
        }
    }

    private void addValidations(Sheet sheet, int categoryCount) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        for (int i = 0; i < COLUMNS.length; i++) {
            ImportColumn column = COLUMNS[i];
            DataValidationConstraint constraint;
            String title;
            String message;
            switch (column) {
                case CATEGORY -> {
                    if (categoryCount == 0) {
                        continue;
                    }
                    constraint = helper.createFormulaListConstraint("'" + LISTS_SHEET + "'!$A$2:$A$" + (categoryCount + 1));
                    title = "Categoría no válida";
                    message = "Elige una categoría de la lista desplegable.";
                }
                case TAX_RATE -> {
                    constraint = helper.createExplicitListConstraint(new String[]{"0", "5", "19"});
                    title = "IVA no válido";
                    message = "El IVA debe ser 0, 5 o 19.";
                }
                case PURCHASE_PRICE, SALE_PRICE -> {
                    constraint = helper.createDecimalConstraint(DataValidationConstraint.OperatorType.GREATER_OR_EQUAL, "0", null);
                    title = "Precio no válido";
                    message = "Escribe un número mayor o igual a 0, sin letras.";
                }
                case STOCK, MIN_STOCK -> {
                    constraint = helper.createIntegerConstraint(DataValidationConstraint.OperatorType.GREATER_OR_EQUAL, "0", null);
                    title = "Cantidad no válida";
                    message = "Escribe un número entero mayor o igual a 0.";
                }
                default -> {
                    continue;
                }
            }
            DataValidation validation = helper.createValidation(constraint, new CellRangeAddressList(1, MAX_ROWS, i, i));
            validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
            validation.createErrorBox(title, message);
            validation.setShowErrorBox(true);
            sheet.addValidationData(validation);
        }
    }

    private void writeLists(Sheet lists, List<String> categoryNames) {
        lists.createRow(0).createCell(0).setCellValue("Categorías");
        for (int i = 0; i < categoryNames.size(); i++) {
            lists.createRow(i + 1).createCell(0).setCellValue(categoryNames.get(i));
        }
        lists.setColumnWidth(0, 40 * 256);
    }

    private void writeInstructions(Sheet sheet, Styles styles, List<String> categoryNames) {
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 16 * 256);
        sheet.setColumnWidth(2, 100 * 256);
        int r = 0;
        Row title = sheet.createRow(r++);
        title.createCell(0).setCellValue("Importación masiva de productos e inventario");
        title.getCell(0).setCellStyle(styles.title);
        title.setHeightInPoints(26);
        r++;

        String[] steps = {
                "1. Llena la hoja «Productos»: una fila por producto. No cambies los encabezados de la fila 1 "
                        + "(pasa el mouse por cada encabezado para ver una ayuda).",
                "2. Si el código interno NO existe en tu local, se crea un producto nuevo. Si YA existe, se actualiza: "
                        + "las celdas que llenes reemplazan el valor actual y las que dejes vacías lo conservan.",
                "3. Columna Stock: en un producto nuevo es el stock inicial; en uno existente es la cantidad que contaste. "
                        + "El sistema ajusta el inventario a ese valor y deja el movimiento en el kardex. Vacía = no se toca.",
                "4. Para hacer un conteo de inventario descarga la plantilla «con mis productos»: trae tus productos y su "
                        + "stock actual. Corrige solo la columna Stock con lo que contaste y súbela.",
                "5. Todo o nada: antes de importar verás una vista previa fila por fila. Si alguna fila tiene errores no se "
                        + "importa nada hasta que corrijas el archivo. Subir el mismo archivo dos veces no duplica productos ni stock.",
                "6. Máximo " + MAX_ROWS_LABEL + " filas por archivo. Guárdalo como "
                        + "«Libro de Excel (.xlsx)».",
                "7. Precios: mejor sin puntos de miles (12500). También se aceptan $12.500 o 12.500,50. "
                        + "IVA: 0, 5 o 19. Stock y stock mínimo: números enteros.",
        };
        for (String step : steps) {
            Row row = sheet.createRow(r++);
            Cell cell = row.createCell(0);
            cell.setCellValue(step);
            cell.setCellStyle(styles.wrap);
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, 2));
            row.setHeightInPoints(32);
        }
        r++;

        Row tableHeader = sheet.createRow(r++);
        String[] headers = {"Columna", "Obligatoria", "Qué poner"};
        for (int i = 0; i < headers.length; i++) {
            tableHeader.createCell(i).setCellValue(headers[i]);
            tableHeader.getCell(i).setCellStyle(styles.requiredHeader);
        }
        for (ImportColumn column : COLUMNS) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(column.headerText());
            row.createCell(1).setCellValue(column == ImportColumn.INTERNAL_CODE ? "Sí"
                    : column.requiredForNew ? "Sí, en nuevos" : "No");
            Cell help = row.createCell(2);
            help.setCellValue(column.help);
            help.setCellStyle(styles.wrap);
        }
        r++;

        Row exampleTitle = sheet.createRow(r++);
        exampleTitle.createCell(0).setCellValue("Ejemplo (no lo copies tal cual: en tu archivo los datos van en la hoja «Productos»)");
        exampleTitle.getCell(0).setCellStyle(styles.bold);
        Row exampleHeader = sheet.createRow(r++);
        for (int i = 0; i < COLUMNS.length; i++) {
            exampleHeader.createCell(i).setCellValue(COLUMNS[i].headerText());
            exampleHeader.getCell(i).setCellStyle(COLUMNS[i].requiredForNew ? styles.requiredHeader : styles.optionalHeader);
        }
        String groceries = pick(categoryNames, "Alimentos y Abarrotes");
        String drinks = pick(categoryNames, "Bebidas");
        String[][] examples = {
                {"P-0001", "Arroz Diana 500g", groceries, "", "7702011477014", "2500", "3200", "0", "40", "10", "", ""},
                {"P-0002", "Gaseosa 400ml", drinks, "Gaseosas", "7702090012345", "1800", "2500", "19", "24", "6", "", ""},
        };
        for (String[] example : examples) {
            Row row = sheet.createRow(r++);
            for (int i = 0; i < example.length; i++) {
                row.createCell(i).setCellValue(example[i]);
            }
        }
    }

    private static String pick(List<String> names, String preferred) {
        if (names.contains(preferred)) {
            return preferred;
        }
        return names.isEmpty() ? preferred : names.get(0);
    }

    /** Estilos compartidos (un workbook admite pocos miles de estilos: se crean una sola vez). */
    private static final class Styles {
        final CellStyle requiredHeader;
        final CellStyle optionalHeader;
        final CellStyle text;
        final CellStyle wrap;
        final CellStyle title;
        final CellStyle bold;

        Styles(XSSFWorkbook workbook) {
            XSSFFont white = workbook.createFont();
            white.setBold(true);
            white.setColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, new DefaultIndexedColorMap()));
            requiredHeader = header(workbook, white, new byte[]{(byte) 0x1F, (byte) 0x29, (byte) 0x37});

            XSSFFont dark = workbook.createFont();
            dark.setBold(true);
            optionalHeader = header(workbook, dark, new byte[]{(byte) 0xE5, (byte) 0xE7, (byte) 0xEB});

            text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));

            wrap = workbook.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);

            XSSFFont big = workbook.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 16);
            title = workbook.createCellStyle();
            title.setFont(big);

            XSSFFont boldFont = workbook.createFont();
            boldFont.setBold(true);
            bold = workbook.createCellStyle();
            bold.setFont(boldFont);
        }

        private static CellStyle header(XSSFWorkbook workbook, XSSFFont font, byte[] rgb) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap()));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }
    }

    // ---------------------------------------------------------------- lectura

    /**
     * Filas con datos de la hoja Productos. Las filas totalmente vacías se saltan.
     *
     * @throws IllegalArgumentException si el archivo no es un .xlsx, no tiene la columna de código o supera el límite
     */
    List<ImportRow> read(InputStream xlsx) {
        Workbook workbook;
        try {
            workbook = new XSSFWorkbook(xlsx);
        } catch (Exception e) {
            // POI lanza tipos muy distintos según cómo esté roto el archivo (zip inválido, .xls viejo, xml corrupto).
            throw new IllegalArgumentException(
                    "El archivo no es un Excel (.xlsx) válido. Ábrelo en Excel, guárdalo como «Libro de Excel (.xlsx)» y vuelve a subirlo.");
        }
        try (workbook) {
            Sheet sheet = findProductsSheet(workbook);
            Map<Integer, ImportColumn> columns = readHeader(sheet);
            List<ImportRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<ImportColumn, ImportCell> cells = new EnumMap<>(ImportColumn.class);
                columns.forEach((index, column) -> {
                    ImportCell cell = readCell(row.getCell(index));
                    if (!cell.isBlank()) {
                        cells.put(column, cell);
                    }
                });
                if (cells.isEmpty()) {
                    continue;
                }
                if (rows.size() == MAX_ROWS) {
                    throw new IllegalArgumentException("El archivo tiene más de " + MAX_ROWS_LABEL
                            + " filas con productos. Divídelo en varios archivos.");
                }
                rows.add(new ImportRow(r + 1, cells));
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Sheet findProductsSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheet(PRODUCTS_SHEET);
        if (sheet != null) {
            return sheet;
        }
        for (Sheet candidate : workbook) {
            String name = candidate.getSheetName();
            if (!name.equalsIgnoreCase(INSTRUCTIONS_SHEET) && !name.equalsIgnoreCase(LISTS_SHEET)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("No se encontró la hoja «" + PRODUCTS_SHEET + "» en el archivo.");
    }

    private static Map<Integer, ImportColumn> readHeader(Sheet sheet) {
        Row header = sheet.getRow(0);
        Map<Integer, ImportColumn> columns = new HashMap<>();
        if (header != null) {
            DataFormatter formatter = new DataFormatter();
            for (Cell cell : header) {
                ImportColumn column = ImportColumn.fromHeader(formatter.formatCellValue(cell));
                if (column == null) {
                    continue;
                }
                if (columns.containsValue(column)) {
                    throw new IllegalArgumentException("La columna «" + column.label + "» aparece dos veces en los encabezados.");
                }
                columns.put(cell.getColumnIndex(), column);
            }
        }
        if (!columns.containsValue(ImportColumn.INTERNAL_CODE)) {
            throw new IllegalArgumentException("No se encontró la columna «" + ImportColumn.INTERNAL_CODE.label
                    + "» en la fila 1 de la hoja «" + sheet.getSheetName() + "». Usa la plantilla descargada del sistema.");
        }
        return columns;
    }

    private static ImportCell readCell(Cell cell) {
        if (cell == null) {
            return ImportCell.EMPTY;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> {
                String value = cell.getStringCellValue();
                yield value == null || value.isBlank() ? ImportCell.EMPTY : new ImportCell(value, null, null);
            }
            case NUMERIC -> {
                // BigDecimal.valueOf usa la representación más corta del double: 7701234567890 sigue siendo
                // 7701234567890 (y no 7.70123E+12, que es lo que muestra Excel con formato General).
                BigDecimal number = BigDecimal.valueOf(cell.getNumericCellValue());
                yield new ImportCell(number.stripTrailingZeros().toPlainString(), number, null);
            }
            case BOOLEAN -> new ImportCell(String.valueOf(cell.getBooleanCellValue()), null, null);
            case ERROR -> new ImportCell(null, null, FormulaError.forInt(cell.getErrorCellValue()).getString());
            default -> ImportCell.EMPTY;
        };
    }
}
