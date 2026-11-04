package com.sciencebot.pos.inventory.internal.services;

import com.sciencebot.pos.products.ProductDto;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductImportWorkbookTest {

    private final ProductImportWorkbook workbook = new ProductImportWorkbook();

    @Test
    void template_hasInstructionsProductsAndHiddenLists() throws IOException {
        byte[] file = workbook.writeTemplate(List.of("Alimentos y Abarrotes", "Bebidas"), List.of());

        try (XSSFWorkbook xlsx = new XSSFWorkbook(new ByteArrayInputStream(file))) {
            assertEquals("Instrucciones", xlsx.getSheetName(0));
            assertEquals("Productos", xlsx.getSheetName(1));
            assertEquals(1, xlsx.getActiveSheetIndex(), "se abre en la hoja Productos");
            assertTrue(xlsx.isSheetHidden(xlsx.getSheetIndex("Listas")));
            assertEquals("Bebidas", xlsx.getSheet("Listas").getRow(2).getCell(0).getStringCellValue());

            Sheet products = xlsx.getSheet("Productos");
            assertEquals("Código interno *", products.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Subcategoría", products.getRow(0).getCell(3).getStringCellValue());
            assertEquals(0, products.getLastRowNum(), "la plantilla vacía no trae filas de ejemplo en Productos");

            List<? extends DataValidation> validations = ((XSSFSheet) products).getDataValidations();
            DataValidation category = validations.stream()
                    .filter(v -> v.getRegions().getCellRangeAddress(0).getFirstColumn() == ImportColumn.CATEGORY.ordinal())
                    .findFirst().orElseThrow();
            assertEquals("'Listas'!$A$2:$A$3", category.getValidationConstraint().getFormula1());
            assertEquals(ProductImportWorkbook.MAX_ROWS, category.getRegions().getCellRangeAddress(0).getLastRow());
            assertEquals(6, validations.size(), "categoría, IVA, 2 precios y 2 cantidades");
            // En OOXML showDropDown="1" OCULTA la flecha de la lista (al revés de lo que dice el nombre).
            ((XSSFSheet) products).getCTWorksheet().getDataValidations().getDataValidationList()
                    .forEach(v -> assertFalse(v.isSetShowDropDown() && v.getShowDropDown(), "la lista desplegable debe verse"));
        }
    }

    @Test
    void template_withProducts_roundTripsThroughTheReader() {
        ProductDto product = new ProductDto(1L, "00123", "7701234567890", "Arroz", "Bebidas", null, null,
                new BigDecimal("2500.00"), new BigDecimal("3200.00"), 40, 10, true, null, new BigDecimal("19.00"), null,
                2L, "Arroz blanco");

        List<ImportRow> rows = workbook.read(new ByteArrayInputStream(workbook.writeTemplate(List.of("Bebidas"), List.of(product))));

        assertEquals(1, rows.size());
        ImportRow row = rows.get(0);
        assertEquals(2, row.rowNumber());
        assertEquals("00123", row.cell(ImportColumn.INTERNAL_CODE).text(), "los ceros a la izquierda se conservan");
        assertEquals("7701234567890", row.cell(ImportColumn.BARCODE).text());
        assertEquals(0, new BigDecimal("3200").compareTo(row.cell(ImportColumn.SALE_PRICE).number()));
        assertEquals(0, BigDecimal.valueOf(40).compareTo(row.cell(ImportColumn.STOCK).number()));
        assertEquals("Arroz blanco", row.cell(ImportColumn.DESCRIPTION).text());
        assertTrue(row.cell(ImportColumn.SUBCATEGORY).isBlank());
    }
}
