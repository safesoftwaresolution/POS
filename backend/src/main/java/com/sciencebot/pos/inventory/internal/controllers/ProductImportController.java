package com.sciencebot.pos.inventory.internal.controllers;

import com.sciencebot.pos.inventory.ProductImportFacade;
import com.sciencebot.pos.inventory.ProductImportPreviewDto;
import com.sciencebot.pos.inventory.ProductImportRejectedException;
import com.sciencebot.pos.inventory.ProductImportResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api/v1/inventory/import")
@Tag(name = "📊 Inventario", description = "Movimientos de inventario: entradas, salidas y ajustes")
public class ProductImportController {

    static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Una plantilla con 5.000 productos pesa unos cientos de KB; el tope acota la memoria que usa POI al abrirla. */
    static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private final ProductImportFacade productImportFacade;

    public ProductImportController(ProductImportFacade productImportFacade) {
        this.productImportFacade = productImportFacade;
    }

    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Descargar plantilla de importación masiva",
            description = """
                    Devuelve la plantilla Excel (.xlsx) para crear o actualizar productos en bloque: hoja
                    `Productos`, hoja `Instrucciones` y listas desplegables de categorías e IVA.

                    Con `withProducts=true` la hoja `Productos` viene llena con los productos activos del local y
                    su stock actual, para hacer un conteo físico: se corrige la columna Stock y se sube el archivo.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Archivo .xlsx", content = @Content(mediaType = XLSX_MEDIA_TYPE)),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<byte[]> downloadTemplate(
            @Parameter(description = "Incluir los productos activos del local", example = "false")
            @RequestParam(defaultValue = "false") boolean withProducts
    ) {
        byte[] file = productImportFacade.buildTemplate(withProducts);
        String filename = withProducts ? "inventario-productos.xlsx" : "plantilla-productos.xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(file);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Vista previa de una importación masiva",
            description = "Valida el archivo y describe qué pasaría con cada fila (CREATE, UPDATE, UNCHANGED o ERROR) sin guardar nada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vista previa por fila",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductImportPreviewDto.class))),
            @ApiResponse(responseCode = "400", description = "El archivo no es un .xlsx válido, no tiene la columna de código, está vacío o supera el límite", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<ProductImportPreviewDto> preview(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(withContent(file, productImportFacade::preview));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Importar productos e inventario",
            description = """
                    Vuelve a validar el archivo e importa todo en una sola transacción ("todo o nada"):
                    - Código interno nuevo: crea el producto; su stock inicial entra como movimiento `ENTRY`.
                    - Código existente: actualiza los datos que vengan llenos y toma la columna Stock como conteo
                      físico (la diferencia entra como `AJUSTE` o `SALIDA`).

                    Si alguna fila tiene errores responde 422 con la vista previa y no guarda nada.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Importación aplicada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductImportResultDto.class))),
            @ApiResponse(responseCode = "400", description = "El archivo no es un .xlsx válido, no tiene la columna de código, está vacío o supera el límite", content = @Content),
            @ApiResponse(responseCode = "409", description = "Algo cambió mientras se importaba (p. ej. stock insuficiente); no se guardó nada", content = @Content),
            @ApiResponse(responseCode = "422", description = "Hay filas con errores; el cuerpo trae `preview` con el detalle y no se guardó nada", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<ProductImportResultDto> importProducts(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(withContent(file, productImportFacade::importProducts));
    }

    @ExceptionHandler(ProductImportRejectedException.class)
    public ResponseEntity<Map<String, Object>> handleRejected(ProductImportRejectedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Unprocessable Entity");
        body.put("message", ex.getMessage());
        body.put("preview", ex.getPreview());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(body);
    }

    private static <T> T withContent(MultipartFile file, Function<InputStream, T> action) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un archivo .xlsx para importar.");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        if (!filename.endsWith(".xlsx")) {
            throw new IllegalArgumentException("Solo se aceptan archivos de Excel .xlsx (usa la plantilla descargada del sistema).");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("El archivo pesa más de 2 MB. Divídelo en varios archivos.");
        }
        try (InputStream content = file.getInputStream()) {
            return action.apply(content);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
    }
}
