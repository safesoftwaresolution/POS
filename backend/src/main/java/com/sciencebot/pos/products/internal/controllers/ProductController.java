package com.sciencebot.pos.products.internal.controllers;

import com.sciencebot.pos.products.CreateProductCommand;
import com.sciencebot.pos.products.ProductDto;
import com.sciencebot.pos.products.ProductFacade;
import com.sciencebot.pos.products.ProductLotsDto;
import com.sciencebot.pos.products.UpdateProductCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "📦 Productos", description = "Catálogo de productos, precios y disponibilidad")
public class ProductController {

    private final ProductFacade productFacade;

    public ProductController(ProductFacade productFacade) {
        this.productFacade = productFacade;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Buscar productos",
            description = """
                    Busca y lista productos con soporte de paginación y filtros opcionales.
                    - `search` — busca por nombre, código interno o código de barras (búsqueda parcial)
                    - `categoryId` — filtra por categoría
                    - `active` — filtra por estado (`true` = activos, `false` = inactivos)
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de productos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Page<ProductDto>> searchProducts(
            @Parameter(description = "Texto de búsqueda (nombre, código interno, código de barras)", example = "Arroz")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filtrar por ID de categoría", example = "2")
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Filtrar por estado activo", example = "true")
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Número de página (desde 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductDto> products = productFacade.searchProducts(search, categoryId, active, pageable);
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Obtener producto por ID",
            description = "Retorna el detalle completo de un producto específico."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    public ResponseEntity<ProductDto> getProductById(
            @Parameter(description = "ID del producto", example = "1", required = true)
            @PathVariable Long id) {
        return productFacade.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Crear producto",
            description = "Registra un nuevo producto en el catálogo. Requiere rol **ADMINISTRATOR** o **SUPERVISOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Producto creado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<ProductDto> createProduct(@RequestBody CreateProductCommand command) {
        ProductDto created = productFacade.createProduct(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Actualizar producto",
            description = "Actualiza todos los campos de un producto existente. Requiere rol **ADMINISTRATOR** o **SUPERVISOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Producto actualizado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductDto.class))),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<ProductDto> updateProduct(
            @Parameter(description = "ID del producto a actualizar", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody UpdateProductCommand command
    ) {
        ProductDto updated = productFacade.updateProduct(id, command);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Cambiar estado del producto",
            description = """
                    Activa o desactiva un producto sin eliminarlo del sistema.
                    Un producto inactivo no aparece en el punto de venta.
                    
                    **Cuerpo requerido:** `{ "active": true }` o `{ "active": false }`
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Estado cambiado correctamente", content = @Content),
            @ApiResponse(responseCode = "400", description = "Campo 'active' requerido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Void> changeStatus(
            @Parameter(description = "ID del producto", example = "1", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Estado a asignar al producto",
                    content = @Content(schema = @Schema(example = "{\"active\": true}"))
            )
            @RequestBody Map<String, Boolean> statusMap
    ) {
        Boolean active = statusMap.get("active");
        if (active == null) {
            throw new IllegalArgumentException("El campo 'active' es requerido");
        }
        productFacade.changeStatus(id, active);
        return ResponseEntity.noContent().build();
    }

    /**
     * Cuerpo para crear o corregir un lote. Al crear (POST) la cantidad es obligatoria; al corregir (PUT) es opcional:
     * sin ella las unidades del lote no cambian.
     */
    @Schema(description = "Datos de un lote de vencimiento")
    public record ProductLotRequest(
            @Schema(description = "Unidades del lote. Obligatoria al crear; al corregir es opcional (sin ella no cambian)", example = "10")
            Integer quantity,
            @Schema(description = "Fecha de vencimiento del lote", example = "2027-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate expirationDate,
            @Schema(description = "Código de lote del proveedor (opcional, máx. 50)", example = "L-2210")
            String lotCode
    ) {}

    @GetMapping("/{id}/lots")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Lotes de vencimiento de un producto",
            description = """
                    Lista los lotes que todavía tienen unidades, del que vence primero al último, con las unidades
                    que quedan de cada uno (se asume que se vende primero lo que vence primero), y cuántas unidades
                    del stock no tienen fecha (`undatedQuantity`). Requiere rol **ADMINISTRATOR** o **SUPERVISOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lotes del producto",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductLotsDto.class))),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<ProductLotsDto> listLots(
            @Parameter(description = "ID del producto", example = "1", required = true) @PathVariable Long id) {
        return ResponseEntity.ok(productFacade.listLots(id));
    }

    @PostMapping("/{id}/lots")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Ponerle fecha de vencimiento a stock que ya existe",
            description = """
                    Crea un lote para unidades que ya están en el inventario sin fecha. No mueve stock: la cantidad no
                    puede superar las unidades sin fecha del producto. Las entradas nuevas con vencimiento se
                    registran desde *Mover stock* o *Compras*. Requiere rol **ADMINISTRATOR** o **SUPERVISOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lote creado", content = @Content),
            @ApiResponse(responseCode = "400", description = "Cantidad mayor a las unidades sin fecha, fecha inválida", content = @Content),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Void> assignLot(
            @Parameter(description = "ID del producto", example = "1", required = true) @PathVariable Long id,
            @RequestBody ProductLotRequest request) {
        if (request.quantity() == null) {
            throw new IllegalArgumentException("La cantidad del lote es obligatoria");
        }
        productFacade.assignLot(id, request.quantity(), request.expirationDate(), request.lotCode());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/lots/{lotId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Corregir la fecha, el código o las unidades de un lote",
            description = """
                    Cambia la fecha de vencimiento y el código de un lote y, si se envía `quantity`, las unidades que
                    le quedan. El stock del producto no cambia: subir las unidades las toma de las "sin fecha" (falla
                    si no alcanzan) y bajarlas las devuelve a ese grupo. Requiere rol **ADMINISTRATOR** o **SUPERVISOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lote actualizado", content = @Content),
            @ApiResponse(responseCode = "400", description = "Fecha inválida, unidades inválidas o mayores a las unidades sin fecha", content = @Content),
            @ApiResponse(responseCode = "404", description = "Producto o lote no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Void> updateLot(
            @Parameter(description = "ID del producto", example = "1", required = true) @PathVariable Long id,
            @Parameter(description = "ID del lote", example = "7", required = true) @PathVariable Long lotId,
            @RequestBody ProductLotRequest request) {
        productFacade.updateLot(id, lotId, request.expirationDate(), request.lotCode(), request.quantity());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/lots/{lotId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Quitar un lote",
            description = """
                    Elimina el registro del lote. El stock no cambia: esas unidades pasan a "sin fecha". Para dar de baja
                    mercancía vencida use una salida por *Vencimiento* en Inventario. Requiere rol **ADMINISTRATOR** o **SUPERVISOR**.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Lote eliminado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Producto o lote no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Void> deleteLot(
            @Parameter(description = "ID del producto", example = "1", required = true) @PathVariable Long id,
            @Parameter(description = "ID del lote", example = "7", required = true) @PathVariable Long lotId) {
        productFacade.deleteLot(id, lotId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Eliminar producto",
            description = "Elimina permanentemente un producto del catálogo. Solo accesible para **ADMINISTRATOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Producto eliminado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Producto no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Solo ADMINISTRATOR puede eliminar productos", content = @Content)
    })
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "ID del producto a eliminar", example = "1", required = true)
            @PathVariable Long id) {
        productFacade.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
