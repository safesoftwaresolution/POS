package com.sciencebot.pos.inventory.internal.services;

import com.sciencebot.pos.categories.CategoryDto;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.categories.CreateSubcategoryCommand;
import com.sciencebot.pos.categories.SubcategoryDto;
import com.sciencebot.pos.categories.SubcategoryFacade;
import com.sciencebot.pos.inventory.ProductImportFacade;
import com.sciencebot.pos.inventory.ProductImportPreviewDto;
import com.sciencebot.pos.inventory.ProductImportRejectedException;
import com.sciencebot.pos.inventory.ProductImportResultDto;
import com.sciencebot.pos.inventory.ProductImportRowDto;
import com.sciencebot.pos.inventory.internal.services.ImportRow.ImportCell;
import com.sciencebot.pos.products.CreateProductCommand;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.products.UpdateProductCommand;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sciencebot.pos.inventory.internal.services.ProductImportRowParser.normalizeKey;

/**
 * Importación masiva de productos e inventario desde la plantilla Excel (ver {@link ProductImportFacade}).
 *
 * <p>El stock nunca se escribe en la entidad: los productos nuevos se crean con stock 0 y el inicial entra como
 * movimiento {@code ENTRY}; en los existentes la diferencia contra el conteo entra como {@code AJUSTE} (sube) o
 * {@code SALIDA} (baja). Todo pasa por {@code ProductFacade.adjustStock} (UPDATE atómico) y queda en el kardex.
 * El ajuste se calcula contra el stock leído al validar el archivo: si entre tanto hubo ventas, se descuentan
 * del conteo en vez de borrarse.
 */
@Service
@Transactional(readOnly = true)
public class ProductImportServiceImpl implements ProductImportFacade {

    static final String ACTION_CREATE = "CREATE";
    static final String ACTION_UPDATE = "UPDATE";
    static final String ACTION_UNCHANGED = "UNCHANGED";
    static final String ACTION_ERROR = "ERROR";
    static final String REASON_INITIAL_STOCK = "Stock inicial (importación masiva)";
    static final String REASON_PHYSICAL_COUNT = "Conteo físico (importación masiva)";
    private static final int DEFAULT_MIN_STOCK = 5;
    private static final int TEMPLATE_PAGE_SIZE = 500;

    private final ProductImportWorkbook workbook;
    private final ProductFacade productFacade;
    private final CategoryFacade categoryFacade;
    private final SubcategoryFacade subcategoryFacade;
    private final InventoryServiceImpl inventoryService;

    public ProductImportServiceImpl(
            ProductImportWorkbook workbook,
            @Lazy ProductFacade productFacade,
            @Lazy CategoryFacade categoryFacade,
            @Lazy SubcategoryFacade subcategoryFacade,
            InventoryServiceImpl inventoryService
    ) {
        this.workbook = workbook;
        this.productFacade = productFacade;
        this.categoryFacade = categoryFacade;
        this.subcategoryFacade = subcategoryFacade;
        this.inventoryService = inventoryService;
    }

    @Override
    public byte[] buildTemplate(boolean withProducts) {
        List<String> categoryNames = categoryFacade.listAll().stream()
                .map(CategoryDto::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return workbook.writeTemplate(categoryNames, withProducts ? activeProducts() : List.of());
    }

    @Override
    public ProductImportPreviewDto preview(InputStream xlsx) {
        return analyze(readRows(xlsx)).preview();
    }

    @Override
    @Transactional
    public ProductImportResultDto importProducts(InputStream xlsx) {
        Analysis analysis = analyze(readRows(xlsx));
        ProductImportPreviewDto preview = analysis.preview();
        if (preview.hasErrors()) {
            throw new ProductImportRejectedException(preview);
        }
        return apply(analysis);
    }

    // ---------------------------------------------------------------- aplicar

    private ProductImportResultDto apply(Analysis analysis) {
        Map<SubcategoryKey, Long> createdSubcategories = new HashMap<>();
        analysis.subcategoriesToCreate.forEach((key, name) -> createdSubcategories.put(key,
                subcategoryFacade.createSubcategory(new CreateSubcategoryCommand(key.categoryId(), name, null)).id()));

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int movements = 0;
        int lowStock = 0;
        for (RowPlan plan : analysis.plans) {
            try {
                Long subcategoryId = plan.newSubcategory != null
                        ? createdSubcategories.get(plan.newSubcategory)
                        : plan.subcategoryId;
                switch (plan.action) {
                    case ACTION_CREATE -> {
                        ProductDto product = productFacade.createProduct(new CreateProductCommand(
                                plan.internalCode, plan.barcode, plan.name, plan.categoryId, subcategoryId,
                                plan.purchasePrice, plan.salePrice, plan.minStock, 0, plan.description, null,
                                plan.taxRate, plan.unspscCode));
                        if (plan.finalStock > 0) {
                            inventoryService.registerMovement(product.id(), "ENTRY", plan.finalStock, REASON_INITIAL_STOCK, false);
                            movements++;
                        }
                        created++;
                    }
                    case ACTION_UPDATE -> {
                        ProductDto existing = plan.existing;
                        // Primero los datos y después el stock: adjustStock hace flush del update pendiente antes de
                        // su UPDATE atómico, y la entidad no se vuelve a tocar (no puede pisar el stock nuevo).
                        if (plan.dataChanged) {
                            productFacade.updateProduct(existing.id(), new UpdateProductCommand(
                                    plan.name, plan.description, plan.categoryId, subcategoryId,
                                    plan.purchasePrice, plan.salePrice, plan.barcode, plan.minStock,
                                    existing.imageUrl(), plan.taxRate, plan.unspscCode));
                        }
                        int delta = plan.finalStock - existing.quantityAvailable();
                        if (delta != 0) {
                            inventoryService.registerMovement(existing.id(), delta > 0 ? "AJUSTE" : "SALIDA",
                                    Math.abs(delta), REASON_PHYSICAL_COUNT, false);
                            movements++;
                        }
                        updated++;
                    }
                    default -> unchanged++;
                }
                if (plan.finalStock <= plan.minStock) {
                    lowStock++;
                }
            } catch (RuntimeException e) {
                // Algo cambió entre la validación y la escritura (p. ej. una venta o un código creado en paralelo).
                // Se revierte todo: la importación es "todo o nada".
                throw new IllegalStateException("Fila " + plan.row.rowNumber() + " (código " + plan.internalCode + "): "
                        + e.getMessage() + ". No se importó ningún producto.", e);
            }
        }
        return new ProductImportResultDto(analysis.plans.size(), created, updated, unchanged, movements,
                createdSubcategories.size(), lowStock);
    }

    // ---------------------------------------------------------------- validar

    private List<ImportRow> readRows(InputStream xlsx) {
        List<ImportRow> rows = workbook.read(xlsx);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("El archivo no tiene productos: la hoja «"
                    + ProductImportWorkbook.PRODUCTS_SHEET + "» está vacía.");
        }
        return rows;
    }

    private Analysis analyze(List<ImportRow> rows) {
        Context ctx = new Context();
        for (CategoryDto category : categoryFacade.listAll()) {
            ctx.categoriesByKey.putIfAbsent(normalizeKey(category.name()), category);
        }
        for (SubcategoryDto subcategory : subcategoryFacade.listAll()) {
            ctx.subcategories.putIfAbsent(new SubcategoryKey(subcategory.categoryId(), normalizeKey(subcategory.name())), subcategory);
        }

        // Primera pasada: códigos y códigos de barras, para buscar en lote y detectar repetidos en el archivo.
        for (ImportRow row : rows) {
            String code = quietText(row.cell(ImportColumn.INTERNAL_CODE));
            if (code != null) {
                ctx.rowsByCode.computeIfAbsent(code, k -> new ArrayList<>()).add(row.rowNumber());
            }
            String barcode = quietText(row.cell(ImportColumn.BARCODE));
            if (barcode != null) {
                ctx.rowsByBarcode.computeIfAbsent(barcode, k -> new ArrayList<>()).add(row.rowNumber());
            }
        }
        ctx.existingByCode.putAll(productFacade.findByInternalCodes(ctx.rowsByCode.keySet()).stream()
                .collect(Collectors.toMap(ProductDto::internalCode, Function.identity(), (a, b) -> a)));
        ctx.ownerByBarcode.putAll(productFacade.findByBarcodes(ctx.rowsByBarcode.keySet()).stream()
                .collect(Collectors.toMap(ProductDto::barcode, Function.identity(), (a, b) -> a)));

        List<RowPlan> plans = rows.stream().map(row -> plan(row, ctx)).toList();
        return new Analysis(plans, ctx.subcategoriesToCreate);
    }

    private RowPlan plan(ImportRow row, Context ctx) {
        RowPlan p = new RowPlan(row);

        String code = p.read(ImportColumn.INTERNAL_CODE, c -> ProductImportRowParser.text(c, 50));
        String name = p.read(ImportColumn.NAME, c -> ProductImportRowParser.text(c, 100));
        String categoryText = p.read(ImportColumn.CATEGORY, c -> ProductImportRowParser.text(c, 50));
        String subcategoryText = p.read(ImportColumn.SUBCATEGORY, c -> ProductImportRowParser.text(c, 50));
        String barcode = p.read(ImportColumn.BARCODE, c -> ProductImportRowParser.text(c, 50));
        BigDecimal purchasePrice = p.read(ImportColumn.PURCHASE_PRICE, ProductImportRowParser::price);
        BigDecimal salePrice = p.read(ImportColumn.SALE_PRICE, ProductImportRowParser::price);
        BigDecimal taxRate = p.read(ImportColumn.TAX_RATE, ProductImportRowParser::taxRate);
        Integer stock = p.read(ImportColumn.STOCK, ProductImportRowParser::nonNegativeInteger);
        Integer minStock = p.read(ImportColumn.MIN_STOCK, ProductImportRowParser::nonNegativeInteger);
        String description = p.read(ImportColumn.DESCRIPTION, c -> ProductImportRowParser.text(c, 255));
        String unspscCode = p.read(ImportColumn.UNSPSC_CODE, c -> ProductImportRowParser.text(c, 50));

        p.internalCode = code;
        ProductDto existing = code != null ? ctx.existingByCode.get(code) : null;
        p.existing = existing;

        if (code == null && !p.invalid.contains(ImportColumn.INTERNAL_CODE)) {
            p.errors.add(ImportColumn.INTERNAL_CODE.label + " es obligatorio.");
        } else if (code != null) {
            List<Integer> sameCode = ctx.rowsByCode.get(code);
            if (sameCode.size() > 1) {
                p.errors.add("El código interno " + code + " está repetido en las filas " + joinRows(sameCode) + ".");
            }
        }

        // Productos existentes: celda vacía = conservar el valor actual.
        p.name = name != null ? name : existing != null ? existing.name() : null;
        p.purchasePrice = purchasePrice != null ? purchasePrice : existing != null ? existing.purchasePrice() : BigDecimal.ZERO;
        p.salePrice = salePrice != null ? salePrice : existing != null ? existing.salePrice() : null;
        p.taxRate = taxRate != null ? taxRate
                : existing != null && existing.taxRate() != null ? existing.taxRate() : BigDecimal.ZERO;
        p.minStock = minStock != null ? minStock : existing != null ? existing.minStock() : DEFAULT_MIN_STOCK;
        p.description = description != null ? description : existing != null ? existing.description() : null;
        p.unspscCode = unspscCode != null ? unspscCode : existing != null ? existing.unspscCode() : null;
        p.barcode = barcode != null ? barcode : existing != null ? existing.barcode() : null;
        p.finalStock = stock != null ? stock : existing != null ? existing.quantityAvailable() : 0;

        if (existing == null) {
            p.requireForNew(ImportColumn.NAME, p.name);
            p.requireForNew(ImportColumn.CATEGORY, categoryText);
            p.requireForNew(ImportColumn.SALE_PRICE, p.salePrice);
        }

        resolveCategory(p, ctx, categoryText, subcategoryText);

        if (barcode != null) {
            List<Integer> sameBarcode = ctx.rowsByBarcode.get(barcode);
            if (sameBarcode.size() > 1) {
                p.errors.add("El código de barras " + barcode + " está repetido en las filas " + joinRows(sameBarcode) + ".");
            }
            ProductDto owner = ctx.ownerByBarcode.get(barcode);
            if (owner != null && (existing == null || !owner.id().equals(existing.id()))) {
                p.errors.add("El código de barras " + barcode + " ya lo usa el producto " + owner.internalCode()
                        + " (" + owner.name() + ").");
            }
        }

        if (p.salePrice != null && p.purchasePrice != null && p.salePrice.compareTo(p.purchasePrice) < 0) {
            p.errors.add("El precio de venta (" + plain(p.salePrice) + ") es menor que el precio de compra ("
                    + plain(p.purchasePrice) + ").");
        }

        if (existing != null && !existing.active()) {
            p.warnings.add("El producto está inactivo: se actualizará pero seguirá inactivo.");
        }

        if (!p.errors.isEmpty()) {
            p.action = ACTION_ERROR;
        } else if (existing == null) {
            p.action = ACTION_CREATE;
        } else {
            p.dataChanged = !Objects.equals(p.name, existing.name())
                    || !Objects.equals(p.categoryId, existing.categoryId())
                    || !Objects.equals(p.subcategoryId, existing.subcategoryId())
                    || p.newSubcategory != null
                    || differs(p.purchasePrice, existing.purchasePrice())
                    || differs(p.salePrice, existing.salePrice())
                    || differs(p.taxRate, existing.taxRate() != null ? existing.taxRate() : BigDecimal.ZERO)
                    || !Objects.equals(p.barcode, existing.barcode())
                    || p.minStock != existing.minStock()
                    || !Objects.equals(p.description, existing.description())
                    || !Objects.equals(p.unspscCode, existing.unspscCode());
            boolean stockChanged = p.finalStock != existing.quantityAvailable();
            p.action = p.dataChanged || stockChanged ? ACTION_UPDATE : ACTION_UNCHANGED;
        }
        return p;
    }

    private void resolveCategory(RowPlan p, Context ctx, String categoryText, String subcategoryText) {
        ProductDto existing = p.existing;
        if (categoryText != null) {
            CategoryDto category = ctx.categoriesByKey.get(normalizeKey(categoryText));
            if (category == null) {
                p.errors.add("Categoría: «" + categoryText + "» no existe. Elige una de la lista desplegable.");
                return;
            }
            p.categoryId = category.id();
            p.categoryName = category.name();
        } else if (existing != null) {
            p.categoryId = existing.categoryId();
            p.categoryName = existing.categoryName();
        } else {
            return;
        }

        if (subcategoryText != null) {
            SubcategoryKey key = new SubcategoryKey(p.categoryId, normalizeKey(subcategoryText));
            SubcategoryDto subcategory = ctx.subcategories.get(key);
            if (subcategory != null) {
                p.subcategoryId = subcategory.id();
            } else {
                p.newSubcategory = key;
                // Se crea una sola vez, con el nombre tal como se escribió la primera vez en el archivo.
                String createdName = ctx.subcategoriesToCreate.computeIfAbsent(key, k -> subcategoryText);
                p.warnings.add("Se creará la subcategoría «" + createdName + "» en «" + p.categoryName + "».");
            }
        } else if (existing != null && existing.subcategoryId() != null) {
            if (p.categoryId.equals(existing.categoryId())) {
                p.subcategoryId = existing.subcategoryId();
            } else {
                p.warnings.add("Se quitará la subcategoría «" + existing.subcategoryName() + "» porque cambió la categoría.");
            }
        }
    }

    private List<ProductDto> activeProducts() {
        List<ProductDto> products = new ArrayList<>();
        Page<ProductDto> page;
        int pageNumber = 0;
        do {
            page = productFacade.searchProducts(null, null, true,
                    PageRequest.of(pageNumber++, TEMPLATE_PAGE_SIZE, Sort.by("internalCode")));
            products.addAll(page.getContent());
        } while (page.hasNext());
        return products;
    }

    /** Texto de la celda sin validar (para la primera pasada); los errores se reportan en la segunda. */
    private static String quietText(ImportCell cell) {
        try {
            return ProductImportRowParser.text(cell, Integer.MAX_VALUE);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.compareTo(b) != 0;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String joinRows(List<Integer> rows) {
        return rows.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    // ---------------------------------------------------------------- estado interno

    private record SubcategoryKey(Long categoryId, String normalizedName) {
    }

    /** Datos de consulta compartidos por todas las filas de un archivo. */
    private static final class Context {
        final Map<String, CategoryDto> categoriesByKey = new HashMap<>();
        final Map<SubcategoryKey, SubcategoryDto> subcategories = new HashMap<>();
        final Map<String, List<Integer>> rowsByCode = new LinkedHashMap<>();
        final Map<String, List<Integer>> rowsByBarcode = new LinkedHashMap<>();
        final Map<String, ProductDto> existingByCode = new HashMap<>();
        final Map<String, ProductDto> ownerByBarcode = new HashMap<>();
        /** Subcategorías que no existen todavía, con el nombre tal como se escribió la primera vez. */
        final Map<SubcategoryKey, String> subcategoriesToCreate = new LinkedHashMap<>();
    }

    private record Analysis(List<RowPlan> plans, Map<SubcategoryKey, String> subcategoriesToCreate) {

        ProductImportPreviewDto preview() {
            List<ProductImportRowDto> rows = plans.stream().map(RowPlan::toDto).toList();
            return new ProductImportPreviewDto(
                    rows.size(),
                    count(ACTION_CREATE),
                    count(ACTION_UPDATE),
                    count(ACTION_UNCHANGED),
                    count(ACTION_ERROR),
                    rows);
        }

        private int count(String action) {
            return (int) plans.stream().filter(plan -> plan.action.equals(action)).count();
        }
    }

    /** Lo que se hará con una fila, con los valores ya combinados con los del producto existente. */
    private static final class RowPlan {
        final ImportRow row;
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        /** Columnas con un valor inválido: ya tienen su error y no deben reportarse además como vacías. */
        final Set<ImportColumn> invalid = EnumSet.noneOf(ImportColumn.class);

        String action;
        ProductDto existing;
        String internalCode;
        String name;
        Long categoryId;
        String categoryName;
        Long subcategoryId;
        SubcategoryKey newSubcategory;
        String barcode;
        BigDecimal purchasePrice;
        BigDecimal salePrice;
        BigDecimal taxRate;
        int minStock;
        int finalStock;
        String description;
        String unspscCode;
        boolean dataChanged;

        RowPlan(ImportRow row) {
            this.row = row;
        }

        <T> T read(ImportColumn column, Function<ImportCell, T> parser) {
            try {
                return parser.apply(row.cell(column));
            } catch (IllegalArgumentException e) {
                invalid.add(column);
                errors.add(column.label + ": " + e.getMessage() + ".");
                return null;
            }
        }

        void requireForNew(ImportColumn column, Object value) {
            if (value == null && !invalid.contains(column)) {
                errors.add(column.label + " es obligatorio para un producto nuevo.");
            }
        }

        ProductImportRowDto toDto() {
            Integer currentStock = existing != null ? existing.quantityAvailable() : null;
            Integer newStock = existing == null || finalStock != existing.quantityAvailable() ? finalStock : null;
            return new ProductImportRowDto(row.rowNumber(), internalCode, name, action, currentStock, newStock,
                    List.copyOf(errors), List.copyOf(warnings));
        }
    }
}
