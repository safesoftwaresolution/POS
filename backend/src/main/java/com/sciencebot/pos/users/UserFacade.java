package com.sciencebot.pos.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface UserFacade {
    Optional<UserDto> findByUsername(String username);
    Optional<UserDto> findByEmail(String email);
    UserDto createUser(CreateUserCommand command);
    /**
     * Igual que {@link #createUser} pero con el correo sin verificar: la cuenta no puede iniciar sesion hasta que
     * la persona confirme el enlace que se le envia (invitacion de operadores del backoffice).
     */
    UserDto createUnverifiedUser(CreateUserCommand command);
    UserDto updateUser(Long id, UpdateUserCommand command);
    void deleteUser(Long id);
    void changeStatus(Long id, boolean active);
    void changePassword(Long id, ChangePasswordCommand command);
    UserDto getById(Long id);
    Page<UserDto> listUsers(Pageable pageable);
    Page<UserDto> listUsersByRole(String role, Pageable pageable);

    /** Auto-registro publico: crea un ADMINISTRATOR sin local (storeId=null) y con el correo sin verificar. */
    UserDto registerPendingAdmin(RegisterOwnerCommand command);
    /**
     * Elimina definitivamente a un ADMINISTRATOR que no verificó su correo y no tiene local. Rechaza cualquier
     * otro usuario. El llamador debe borrar antes los tokens que lo referencian (módulo auth).
     */
    void deleteUnverifiedOwner(Long userId);
    /**
     * Borra de la base de datos a un usuario (no es la baja logica de {@link #deleteUser}). Rechaza a un
     * SUPER_ADMIN, a uno mismo y al último administrador activo del local; lanza {@link IllegalStateException} si
     * el usuario ya tiene ventas, compras, movimientos o devoluciones. El llamador borra antes sus tokens.
     */
    void deleteUserPermanently(Long userId);
    /** Historial de borrados definitivos de usuarios, el mas reciente primero (solo lectura). */
    Page<UserDeletionAuditDto> listDeletionAudit(Pageable pageable);
    /** Establece una nueva contrasena sin contexto de sesion (flujo "olvide mi contrasena", ya validado por token). */
    void resetPassword(Long userId, String newPassword);
    /** Marca el correo del usuario como verificado (llamado al consumir el token de verificacion). */
    UserDto markEmailVerified(Long userId);
    /** Asigna el local recien creado a un ADMINISTRATOR que aun no tenia uno. Falla si ya tenia storeId. */
    UserDto assignStore(Long userId, Long storeId);
    /** Usuarios ADMINISTRATOR que aun no verifican su correo (para el panel de pendientes del backoffice). */
    Page<UserDto> listPendingVerificationUsers(Pageable pageable);
    /** Usuarios ADMINISTRATOR que ya verificaron su correo (para el panel de Pendientes del backoffice). */
    Page<UserDto> listVerifiedOwners(Pageable pageable);
    /** Uso de usuarios del local del llamador frente al tope de su plan; vacío si no hay local o tope. */
    Optional<UserQuotaStatusDto> getQuotaStatus();
    /**
     * Sube (o reemplaza) la foto de perfil del usuario autenticado: PNG/JPG, hasta 512 KB y 512x512 px. La foto
     * anterior se borra del almacenamiento una vez confirmada la transacción.
     */
    UserDto updateMyAvatar(org.springframework.web.multipart.MultipartFile file);
    /** Quita la foto de perfil del usuario autenticado (vuelven las iniciales). */
    UserDto removeMyAvatar();
    /** Usuarios activos de un local con alguno de los roles dados (ej. destinatarios de alertas). */
    List<UserDto> listActiveByStoreAndRoles(Long storeId, List<String> roles);
}
