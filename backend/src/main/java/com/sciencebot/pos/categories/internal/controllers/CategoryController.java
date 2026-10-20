package com.sciencebot.pos.categories.internal.controllers;

import com.sciencebot.pos.categories.CategoryDto;
import com.sciencebot.pos.categories.CategoryFacade;
import com.sciencebot.pos.categories.CreateCategoryCommand;
import com.sciencebot.pos.categories.UpdateCategoryCommand;
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
@RequestMapping("/api/v1/categories")
@Tag(name = "Categorias", description = "Gestion de categorias de productos (escritura exclusiva SUPER_ADMIN)")
public class CategoryController {

    private final CategoryFacade categoryFacade;

    public CategoryController(CategoryFacade categoryFacade) {
        this.categoryFacade = categoryFacade;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(
            summary = "Listar todas las categorias",
            description = "Retorna la lista completa de categorias con su conteo de productos asociados. Accesible por todos los roles autenticados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de categorias",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = CategoryDto.class)))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<List<CategoryDto>> listAll() {
        return ResponseEntity.ok(categoryFacade.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Crear categoria",
            description = "Crea una nueva categoria de productos. Requiere rol **SUPER_ADMIN**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria creada exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CategoryDto.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<CategoryDto> createCategory(@RequestBody CreateCategoryCommand command) {
        CategoryDto created = categoryFacade.createCategory(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Actualizar categoria",
            description = "Actualiza el nombre y descripcion de una categoria existente. Requiere rol **SUPER_ADMIN**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria actualizada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CategoryDto.class))),
            @ApiResponse(responseCode = "404", description = "Categoria no encontrada", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<CategoryDto> updateCategory(
            @Parameter(description = "ID de la categoria a actualizar", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody UpdateCategoryCommand command
    ) {
        CategoryDto updated = categoryFacade.updateCategory(id, command);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Eliminar categoria",
            description = "Elimina una categoria permanentemente. Solo para **SUPER_ADMIN**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categoria eliminada correctamente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Categoria no encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "La categoria tiene productos asociados y no puede eliminarse", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Void> deleteCategory(
            @Parameter(description = "ID de la categoria a eliminar", example = "1", required = true)
            @PathVariable Long id) {
        categoryFacade.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}