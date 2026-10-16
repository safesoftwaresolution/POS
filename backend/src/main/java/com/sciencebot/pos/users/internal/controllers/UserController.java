package com.sciencebot.pos.users.internal.controllers;

import com.sciencebot.pos.users.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "👤 Usuarios", description = "Gestión de usuarios del sistema (solo ADMINISTRATOR)")
public class UserController {

    private final UserFacade userFacade;

    public UserController(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Crear usuario",
            description = """
                    Crea un nuevo usuario del sistema. Solo para **ADMINISTRATOR**.
                    
                    **Roles disponibles:** `ADMINISTRATOR`, `SUPERVISOR`, `SELLER`
                    
                    La contraseña se almacena cifrada con BCrypt.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o username duplicado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Solo ADMINISTRATOR puede crear usuarios", content = @Content)
    })
    public ResponseEntity<UserDto> createUser(@jakarta.validation.Valid @RequestBody CreateUserCommand command) {
        UserDto created = userFacade.createUser(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Listar usuarios",
            description = "Retorna una lista paginada de todos los usuarios registrados en el sistema. Solo para **ADMINISTRATOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de usuarios",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Page.class))),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Page<UserDto>> listUsers(Pageable pageable) {
        Page<UserDto> users = userFacade.listUsers(pageable);
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Obtener usuario por ID",
            description = "Retorna el perfil de un usuario específico. Solo para **ADMINISTRATOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<UserDto> getUserById(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long id) {
        UserDto user = userFacade.getById(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Actualizar usuario",
            description = "Actualiza nombre, email y rol de un usuario. Solo para **ADMINISTRATOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario actualizado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = UserDto.class))),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<UserDto> updateUser(
            @Parameter(description = "ID del usuario a actualizar", example = "1", required = true)
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody UpdateUserCommand command) {
        UserDto updated = userFacade.updateUser(id, command);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Cambiar estado del usuario",
            description = """
                    Activa o desactiva un usuario sin eliminarlo.
                    Un usuario inactivo no puede iniciar sesión.
                    
                    **Cuerpo requerido:** `{ "active": true }` o `{ "active": false }`
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Estado cambiado correctamente", content = @Content),
            @ApiResponse(responseCode = "400", description = "Campo 'active' requerido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Void> changeStatus(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Estado a asignar",
                    content = @Content(schema = @Schema(example = "{\"active\": false}"))
            )
            @RequestBody Map<String, Boolean> statusMap) {
        Boolean active = statusMap.get("active");
        if (active == null) {
            throw new IllegalArgumentException("El campo 'active' es requerido");
        }
        userFacade.changeStatus(id, active);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    @Operation(
            summary = "Eliminar usuario",
            description = "Elimina permanentemente un usuario del sistema. Solo para **ADMINISTRATOR**."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Usuario eliminado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes", content = @Content)
    })
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "ID del usuario a eliminar", example = "1", required = true)
            @PathVariable Long id) {
        userFacade.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    @Operation(
            summary = "Cambiar contraseña",
            description = "Permite a un usuario cambiar su propia contraseña, o a un ADMINISTRATOR cambiar la de cualquier usuario."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña actualizada correctamente", content = @Content),
            @ApiResponse(responseCode = "400", description = "La nueva contraseña es requerida", content = @Content),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "No autenticado", content = @Content)
    })
    public ResponseEntity<Void> changePassword(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody ChangePasswordCommand command
    ) {
        if (command == null || command.newPassword() == null || command.newPassword().isBlank()) {
            throw new IllegalArgumentException("La nueva contraseña es requerida");
        }
        userFacade.changePassword(id, command);
        return ResponseEntity.noContent().build();
    }
}
