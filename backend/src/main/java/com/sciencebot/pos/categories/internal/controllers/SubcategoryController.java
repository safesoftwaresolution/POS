package com.sciencebot.pos.categories.internal.controllers;

import com.sciencebot.pos.categories.CreateSubcategoryCommand;
import com.sciencebot.pos.categories.SubcategoryDto;
import com.sciencebot.pos.categories.SubcategoryFacade;
import com.sciencebot.pos.categories.UpdateSubcategoryCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subcategories")
@Tag(name = "Subcategorías", description = "Subcategorías propias de cada tienda, creadas bajo una categoría global")
public class SubcategoryController {

    private final SubcategoryFacade subcategoryFacade;

    public SubcategoryController(SubcategoryFacade subcategoryFacade) {
        this.subcategoryFacade = subcategoryFacade;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Listar subcategorías de la tienda",
            description = "Lista las subcategorías de la tienda actual. Si se pasa `categoryId`, filtra solo las de esa categoría global."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de subcategorías",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = SubcategoryDto.class))))
    })
    public ResponseEntity<List<SubcategoryDto>> list(
            @Parameter(description = "Filtrar por ID de categoría global", example = "2")
            @RequestParam(required = false) Long categoryId
    ) {
        List<SubcategoryDto> result = categoryId != null
                ? subcategoryFacade.listByCategory(categoryId)
                : subcategoryFacade.listAll();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Crear subcategoría",
            description = "Crea una subcategoría propia de la tienda bajo una categoría global existente. Requiere **ADMINISTRATOR** o **SUPERVISOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subcategoría creada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SubcategoryDto.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o nombre duplicado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<SubcategoryDto> create(@RequestBody CreateSubcategoryCommand command) {
        SubcategoryDto created = subcategoryFacade.createSubcategory(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Actualizar subcategoría",
            description = "Actualiza nombre y descripción de una subcategoría de la tienda actual. Requiere **ADMINISTRATOR** o **SUPERVISOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subcategoría actualizada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SubcategoryDto.class))),
            @ApiResponse(responseCode = "404", description = "Subcategoría no encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<SubcategoryDto> update(
            @Parameter(description = "ID de la subcategoría", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody UpdateSubcategoryCommand command
    ) {
        return ResponseEntity.ok(subcategoryFacade.updateSubcategory(id, command));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    @Operation(
            summary = "Eliminar subcategoría",
            description = "Elimina una subcategoría de la tienda actual. Requiere **ADMINISTRATOR** o **SUPERVISOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Subcategoría eliminada", content = @Content),
            @ApiResponse(responseCode = "404", description = "Subcategoría no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "La subcategoría tiene productos asociados", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID de la subcategoría", example = "1", required = true)
            @PathVariable Long id) {
        subcategoryFacade.deleteSubcategory(id);
        return ResponseEntity.noContent().build();
    }
}
