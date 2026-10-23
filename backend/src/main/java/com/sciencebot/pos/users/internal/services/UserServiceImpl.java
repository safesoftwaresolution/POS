package com.sciencebot.pos.users.internal.services;

import com.sciencebot.pos.config.security.PosUserDetails;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.users.*;
import com.sciencebot.pos.users.internal.entities.User;
import com.sciencebot.pos.users.internal.repositories.UserRepository;
import com.sciencebot.pos.users.internal.mappers.UserMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserFacade, UserDetailsService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMINISTRATOR", "SUPERVISOR", "SELLER", "SUPER_ADMIN");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceImpl(
            UserRepository userRepository,
            @org.springframework.context.annotation.Lazy PasswordEncoder passwordEncoder,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        return new PosUserDetails(
                user.getUsername(),
                user.getPassword(),
                user.isActive() && user.isEmailVerified(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())),
                user.getStoreId()
        );
    }

    @Override
    public Optional<UserDto> findByUsername(String username) {
        return userRepository.findByUsername(username).map(userMapper::toDto);
    }

    @Override
    public Optional<UserDto> findByEmail(String email) {
        return userRepository.findByEmail(email).map(userMapper::toDto);
    }

    @Override
    @Transactional
    public UserDto createUser(CreateUserCommand command) {
        validateRole(command.role());
        validatePasswordStrength(command.password());

        // Si quien crea es un ADMINISTRATOR autenticado de un local (hay un local en el
        // contexto de la solicitud), el nuevo usuario SIEMPRE pertenece a ESE local, sin
        // importar que storeId venga en el comando (evita que cree personal en otro local), y
        // jamas puede asignarse a si mismo el rol SUPER_ADMIN a traves de este endpoint. Cuando
        // no hay local en contexto (SUPER_ADMIN vía backoffice, o el seeder en el arranque) se
        // respeta el storeId/rol tal como venían, igual que antes.
        Long callerStoreId = TenantContext.getStoreId();
        Long effectiveStoreId = command.storeId();
        String effectiveRole = command.role().toUpperCase();
        if (callerStoreId != null) {
            if ("SUPER_ADMIN".equals(effectiveRole)) {
                throw new IllegalArgumentException("No tiene permisos para crear un usuario SUPER_ADMIN.");
            }
            effectiveStoreId = callerStoreId;
        }

        if ("SUPER_ADMIN".equals(effectiveRole) && effectiveStoreId != null) {
            throw new IllegalArgumentException("El rol SUPER_ADMIN no puede estar asociado a un local.");
        }
        if (!"SUPER_ADMIN".equals(effectiveRole) && effectiveStoreId == null) {
            throw new IllegalArgumentException("El campo storeId es obligatorio para roles del POS (ADMINISTRATOR, SUPERVISOR, SELLER).");
        }

        if (userRepository.existsByUsername(command.username())) {
            throw new IllegalArgumentException("El nombre de usuario ya esta registrado");
        }
        if (userRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("El correo electronico ya esta registrado");
        }

        User user = new User();
        user.setFullName(command.fullName());
        user.setUsername(command.username());
        user.setEmail(command.email());
        user.setPassword(passwordEncoder.encode(command.password()));
        user.setRole(effectiveRole);
        user.setActive(true);
        user.setStoreId(effectiveStoreId);

        User saved = userRepository.save(user);
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public UserDto updateUser(Long id, UpdateUserCommand command) {
        User user = userRepository.findById(id)
                .filter(this::belongsToCallerStoreOrGlobalCaller)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + id));

        validateRole(command.role());

        if (!user.getEmail().equalsIgnoreCase(command.email()) && userRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("El correo electronico ya esta registrado");
        }

        if (user.getRole().equals("ADMINISTRATOR") && !command.role().equalsIgnoreCase("ADMINISTRATOR")) {
            long adminCount = countActiveAdmins(user.getStoreId());
            if (adminCount <= 1) {
                throw new IllegalArgumentException("No se puede cambiar el rol del ultimo administrador activo");
            }
        }

        user.setFullName(command.fullName());
        user.setEmail(command.email());
        user.setRole(command.role().toUpperCase());

        User saved = userRepository.save(user);
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .filter(this::belongsToCallerStoreOrGlobalCaller)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + id));

        String currentUsername = getCurrentUsername();
        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("No puedes eliminar a tu propio usuario");
        }

        if (user.getRole().equals("ADMINISTRATOR")) {
            long adminCount = countActiveAdmins(user.getStoreId());
            if (adminCount <= 1) {
                throw new IllegalArgumentException("No se puede eliminar al ultimo administrador activo");
            }
        }

        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, boolean active) {
        User user = userRepository.findById(id)
                .filter(this::belongsToCallerStoreOrGlobalCaller)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + id));

        String currentUsername = getCurrentUsername();
        if (user.getUsername().equalsIgnoreCase(currentUsername) && !active) {
            throw new IllegalArgumentException("No puedes desactivar a tu propio usuario");
        }

        if (user.getRole().equals("ADMINISTRATOR")) {
            long adminCount = countActiveAdmins(user.getStoreId());
            if (adminCount <= 1) {
                throw new IllegalArgumentException("No se puede desactivar al ultimo administrador activo");
            }
        }

        user.setActive(active);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void changePassword(Long id, ChangePasswordCommand command) {
        User user = userRepository.findById(id)
                .filter(this::belongsToCallerStoreOrGlobalCaller)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + id));

        String currentUsername = getCurrentUsername();

        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            if (command.currentPassword() == null || command.currentPassword().isBlank()) {
                throw new IllegalArgumentException("La contrasena actual es requerida para cambiar tu contrasena");
            }
            if (!passwordEncoder.matches(command.currentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("La contrasena actual es incorrecta");
            }
        } else {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                throw new org.springframework.security.access.AccessDeniedException("No autenticado");
            }
            boolean isAdminOrSupervisor = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRATOR") || a.getAuthority().equals("ROLE_SUPERVISOR") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
            if (!isAdminOrSupervisor) {
                throw new org.springframework.security.access.AccessDeniedException("No tiene permisos para restablecer la contrasena de otro usuario");
            }
        }

        validatePasswordStrength(command.newPassword());
        user.setPassword(passwordEncoder.encode(command.newPassword()));
        userRepository.save(user);
    }

    @Override
    public UserDto getById(Long id) {
        return userRepository.findById(id)
                .filter(this::belongsToCallerStoreOrGlobalCaller)
                .map(userMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + id));
    }

    @Override
    public Page<UserDto> listUsers(Pageable pageable) {
        Long callerStoreId = TenantContext.getStoreId();
        if (callerStoreId != null) {
            return userRepository.findAllByStoreIdAndActiveTrue(callerStoreId, pageable).map(userMapper::toDto);
        }
        return userRepository.findAllByActiveTrue(pageable)
                .map(userMapper::toDto);
    }

    @Override
    public Page<UserDto> listUsersByRole(String role, Pageable pageable) {
        Long callerStoreId = TenantContext.getStoreId();
        if (callerStoreId != null) {
            return userRepository.findAllByStoreIdAndRole(callerStoreId, role.toUpperCase(), pageable).map(userMapper::toDto);
        }
        return userRepository.findAllByRole(role.toUpperCase(), pageable)
                .map(userMapper::toDto);
    }

    @Override
    @Transactional
    public UserDto registerPendingAdmin(RegisterOwnerCommand command) {
        validatePasswordStrength(command.password());

        if (userRepository.existsByUsername(command.username())) {
            throw new IllegalArgumentException("El nombre de usuario ya esta registrado");
        }
        if (userRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("El correo electronico ya esta registrado");
        }

        User user = new User();
        user.setFullName(command.fullName());
        user.setUsername(command.username());
        user.setEmail(command.email());
        user.setPassword(passwordEncoder.encode(command.password()));
        user.setRole("ADMINISTRATOR");
        user.setActive(true);
        user.setEmailVerified(false);
        user.setStoreId(null);

        User saved = userRepository.save(user);
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + userId));
        validatePasswordStrength(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserDto markEmailVerified(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + userId));
        user.setEmailVerified(true);
        return userMapper.toDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDto assignStore(Long userId, Long storeId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado con ID: " + userId));
        if (user.getStoreId() != null) {
            throw new IllegalArgumentException("El usuario ya tiene un local asignado.");
        }
        user.setStoreId(storeId);
        return userMapper.toDto(userRepository.save(user));
    }

    @Override
    public Page<UserDto> listPendingVerificationUsers(Pageable pageable) {
        return userRepository.findAllByRoleAndEmailVerifiedFalse("ADMINISTRATOR", pageable)
                .map(userMapper::toDto);
    }

    @Override
    public Page<UserDto> listVerifiedOwners(Pageable pageable) {
        return userRepository.findAllByRoleAndEmailVerifiedTrue("ADMINISTRATOR", pageable)
                .map(userMapper::toDto);
    }

    @Override
    public List<UserDto> listActiveByStoreAndRoles(Long storeId, List<String> roles) {
        return userRepository.findAllByStoreIdAndRoleInAndActiveTrue(storeId, roles).stream()
                .map(userMapper::toDto)
                .toList();
    }

    /**
     * Cuando quien llama es un ADMINISTRATOR autenticado (hay un local en el contexto de la
     * solicitud), el usuario objetivo debe pertenecer a ESE local; de lo contrario se trata
     * como inexistente en vez de exponer su existencia en otro local. Cuando no hay local en
     * contexto (SUPER_ADMIN vía backoffice, o una llamada interna del arranque/otros módulos
     * sin request HTTP) se preserva el comportamiento sin restricción anterior.
     */
    private boolean belongsToCallerStoreOrGlobalCaller(User user) {
        Long callerStoreId = TenantContext.getStoreId();
        return callerStoreId == null || callerStoreId.equals(user.getStoreId());
    }

    /** "Ultimo administrador activo" se cuenta por local cuando el usuario pertenece a uno. */
    private long countActiveAdmins(Long storeId) {
        return storeId != null
                ? userRepository.countByStoreIdAndRoleAndActiveTrue(storeId, "ADMINISTRATOR")
                : userRepository.countByRoleAndActiveTrue("ADMINISTRATOR");
    }

    private void validateRole(String role) {
        if (role == null || !ALLOWED_ROLES.contains(role.toUpperCase())) {
            throw new IllegalArgumentException("Rol invalido. Roles permitidos: SUPER_ADMIN, ADMINISTRATOR, SUPERVISOR, SELLER");
        }
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("La contrasena debe tener al menos 8 caracteres");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("La contrasena debe contener al menos una letra y un numero");
        }
    }

    private String getCurrentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getName();
    }
}