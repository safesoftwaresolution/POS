package com.sciencebot.pos.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface UserFacade {
    Optional<UserDto> findByUsername(String username);
    UserDto createUser(CreateUserCommand command);
    UserDto updateUser(Long id, UpdateUserCommand command);
    void deleteUser(Long id);
    void changeStatus(Long id, boolean active);
    void changePassword(Long id, ChangePasswordCommand command);
    UserDto getById(Long id);
    Page<UserDto> listUsers(Pageable pageable);
    Page<UserDto> listUsersByRole(String role, Pageable pageable);

    /** Auto-registro publico: crea un ADMINISTRATOR sin local (storeId=null) y con el correo sin verificar. */
    UserDto registerPendingAdmin(RegisterOwnerCommand command);
    /** Marca el correo del usuario como verificado (llamado al consumir el token de verificacion). */
    UserDto markEmailVerified(Long userId);
    /** Asigna el local recien creado a un ADMINISTRATOR que aun no tenia uno. Falla si ya tenia storeId. */
    UserDto assignStore(Long userId, Long storeId);
    /** Usuarios ADMINISTRATOR que aun no verifican su correo (para el panel de pendientes del backoffice). */
    Page<UserDto> listPendingVerificationUsers(Pageable pageable);
    /** Usuarios activos de un local con alguno de los roles dados (ej. destinatarios de alertas). */
    List<UserDto> listActiveByStoreAndRoles(Long storeId, List<String> roles);
}
