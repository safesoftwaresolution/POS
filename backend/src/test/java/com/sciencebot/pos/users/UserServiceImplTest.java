package com.sciencebot.pos.users;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.users.internal.entities.User;
import com.sciencebot.pos.users.internal.repositories.UserRepository;
import com.sciencebot.pos.users.internal.services.UserServiceImpl;
import com.sciencebot.pos.users.internal.mappers.UserMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserMapper userMapper;
    @Mock private UserQuotaPolicy userQuotaPolicy;
    @InjectMocks private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createUser_Success() {
        CreateUserCommand command = new CreateUserCommand(
                "Juan Perez", "juanp", "juan@tienda.com", "Password123", "SELLER", 1L
        );

        when(userRepository.existsByUsername("juanp")).thenReturn(false);
        when(userRepository.existsByEmail("juan@tienda.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setFullName("Juan Perez");
        savedUser.setUsername("juanp");
        savedUser.setEmail("juan@tienda.com");
        savedUser.setPassword("encodedPassword");
        savedUser.setRole("SELLER");
        savedUser.setActive(true);
        savedUser.setStoreId(1L);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserDto expectedDto = new UserDto(1L, "Juan Perez", "juanp", "juan@tienda.com", "SELLER", true, 1L, true);
        when(userMapper.toDto(any(User.class))).thenReturn(expectedDto);

        UserDto result = userService.createUser(command);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("juanp", result.username());
        assertTrue(result.active());
        assertEquals(1L, result.storeId());
        verify(userRepository, times(1)).save(any(User.class));
    }

    private static final UserQuota BASIC_QUOTA = new UserQuota("BASIC", "Básico", 2);

    private CreateUserCommand sellerCommand() {
        return new CreateUserCommand("Juan Perez", "juanp", "juan@tienda.com", "Password123", "SELLER", 1L);
    }

    @Test
    void createUser_StoreAtPlanLimit_IsRefusedAndSavesNothing() {
        when(userQuotaPolicy.quotaFor(1L)).thenReturn(Optional.of(BASIC_QUOTA));
        when(userRepository.countByStoreIdAndActiveTrue(1L)).thenReturn(2L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(sellerCommand()));

        assertTrue(ex.getMessage().contains("Básico"));
        assertTrue(ex.getMessage().contains("2 usuarios activos"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createUser_StoreBelowPlanLimit_IsAllowed() {
        when(userQuotaPolicy.quotaFor(1L)).thenReturn(Optional.of(BASIC_QUOTA));
        when(userRepository.countByStoreIdAndActiveTrue(1L)).thenReturn(1L);
        when(passwordEncoder.encode("Password123")).thenReturn("enc");
        User saved = new User();
        saved.setId(5L);
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(userMapper.toDto(any(User.class))).thenReturn(new UserDto(5L, "Juan Perez", "juanp", "juan@tienda.com", "SELLER", true, 1L, true));

        assertNotNull(userService.createUser(sellerCommand()));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_StoreWithoutApplicableQuota_IsNotLimited() {
        when(userQuotaPolicy.quotaFor(1L)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password123")).thenReturn("enc");
        User saved = new User();
        saved.setId(5L);
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(userMapper.toDto(any(User.class))).thenReturn(new UserDto(5L, "Juan Perez", "juanp", "juan@tienda.com", "SELLER", true, 1L, true));

        assertNotNull(userService.createUser(sellerCommand()));
        verify(userRepository, never()).countByStoreIdAndActiveTrue(anyLong());
    }

    @Test
    void createUser_SuperAdmin_SkipsQuotaCheck() {
        when(passwordEncoder.encode("Password123")).thenReturn("enc");
        User saved = new User();
        saved.setId(6L);
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(userMapper.toDto(any(User.class))).thenReturn(new UserDto(6L, "Root", "root2", "root2@x.com", "SUPER_ADMIN", true, null, true));

        userService.createUser(new CreateUserCommand("Root", "root2", "root2@x.com", "Password123", "SUPER_ADMIN", null));

        verifyNoInteractions(userQuotaPolicy);
    }

    @Test
    void changeStatus_ReactivatingBeyondPlanLimit_IsRefused() {
        User inactive = new User();
        inactive.setId(9L);
        inactive.setUsername("viejo");
        inactive.setRole("SELLER");
        inactive.setActive(false);
        inactive.setStoreId(1L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(inactive));
        when(userQuotaPolicy.quotaFor(1L)).thenReturn(Optional.of(BASIC_QUOTA));
        when(userRepository.countByStoreIdAndActiveTrue(1L)).thenReturn(2L);

        assertThrows(IllegalArgumentException.class, () -> userService.changeStatus(9L, true));

        assertFalse(inactive.isActive());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changeStatus_Deactivating_DoesNotConsultQuota() {
        User active = new User();
        active.setId(9L);
        active.setUsername("viejo");
        active.setRole("SELLER");
        active.setActive(true);
        active.setStoreId(1L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(active));

        userService.changeStatus(9L, false);

        assertFalse(active.isActive());
        verifyNoInteractions(userQuotaPolicy);
    }

    @Test
    void changeStatus_ReactivatingTheOnlyAdmin_IsAllowed() {
        User inactiveAdmin = new User();
        inactiveAdmin.setId(4L);
        inactiveAdmin.setUsername("dueno");
        inactiveAdmin.setRole("ADMINISTRATOR");
        inactiveAdmin.setActive(false);
        inactiveAdmin.setStoreId(1L);
        when(userRepository.findById(4L)).thenReturn(Optional.of(inactiveAdmin));
        when(userQuotaPolicy.quotaFor(1L)).thenReturn(Optional.empty());

        userService.changeStatus(4L, true);

        assertTrue(inactiveAdmin.isActive());
    }

    private static User userWithRole(Long id, String username, String role, boolean active, Long storeId) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setRole(role);
        u.setActive(active);
        u.setStoreId(storeId);
        return u;
    }

    @Test
    void deleteUserPermanently_RemovesAUserWithoutHistory() {
        User seller = userWithRole(5L, "vendedor", "SELLER", true, 1L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(seller));

        userService.deleteUserPermanently(5L);

        verify(userRepository).delete(seller);
        verify(userRepository).flush();
    }

    @Test
    void deleteUserPermanently_WithBusinessHistory_ThrowsConflictAndKeepsTheUser() {
        User seller = userWithRole(5L, "vendedor", "SELLER", true, 1L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(seller));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("FK_sales_user"))
                .when(userRepository).flush();

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> userService.deleteUserPermanently(5L));

        assertTrue(ex.getMessage().contains("Inactívalo"));
    }

    @Test
    void deleteUserPermanently_RefusesSuperAdminSelfAndTheOnlyStoreAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithRole(1L, "root", "SUPER_ADMIN", true, null)));
        assertThrows(IllegalArgumentException.class, () -> userService.deleteUserPermanently(1L));

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("yo");
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithRole(2L, "yo", "SELLER", true, 1L)));
        assertThrows(IllegalArgumentException.class, () -> userService.deleteUserPermanently(2L));

        when(userRepository.findById(3L)).thenReturn(Optional.of(userWithRole(3L, "dueno", "ADMINISTRATOR", true, 1L)));
        when(userRepository.countByStoreIdAndRoleAndActiveTrue(1L, "ADMINISTRATOR")).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> userService.deleteUserPermanently(3L));

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void getQuotaStatus_ReportsUsageForTheCallerStore() {
        TenantContext.setStoreId(1L);
        when(userQuotaPolicy.quotaFor(1L)).thenReturn(Optional.of(BASIC_QUOTA));
        when(userRepository.countByStoreIdAndActiveTrue(1L)).thenReturn(2L);

        UserQuotaStatusDto status = userService.getQuotaStatus().orElseThrow();

        assertEquals("Básico", status.planName());
        assertEquals(2L, status.activeUsers());
        assertEquals(2, status.maxActiveUsers());
        assertFalse(status.canCreateMore());
    }

    @Test
    void getQuotaStatus_WithoutStoreContext_IsEmpty() {
        assertTrue(userService.getQuotaStatus().isEmpty());
    }

    @Test
    void createUser_WeakPassword_ThrowsException() {
        CreateUserCommand command = new CreateUserCommand(
                "Juan Perez", "juanp", "juan@tienda.com", "weak", "SELLER", 1L
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(command));
        assertTrue(ex.getMessage().contains("caracteres"));
    }

    @Test
    void createUser_SuperAdminWithStoreId_ThrowsException() {
        CreateUserCommand command = new CreateUserCommand(
                "Super Admin", "superadmin", "sa@platform.com", "SuperAdmin1!", "SUPER_ADMIN", 1L
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(command));
        assertTrue(ex.getMessage().contains("SUPER_ADMIN"));
    }

    @Test
    void createUser_PosRoleWithoutStoreId_ThrowsException() {
        CreateUserCommand command = new CreateUserCommand(
                "Juan Perez", "juanp", "juan@tienda.com", "Password123", "SELLER", null
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(command));
        assertTrue(ex.getMessage().contains("storeId"));
    }

    @Test
    void deleteUser_LastAdmin_ThrowsException() {
        User targetUser = new User();
        targetUser.setId(2L);
        targetUser.setUsername("admin2");
        targetUser.setRole("ADMINISTRATOR");
        targetUser.setActive(true);

        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin1");
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        when(userRepository.countByRoleAndActiveTrue("ADMINISTRATOR")).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(2L));
        assertTrue(ex.getMessage().contains("ultimo administrador"));
    }

    @Test
    void changePassword_Self_Success() {
        User user = new User();
        user.setId(1L);
        user.setUsername("juanp");
        user.setPassword("oldEncodedPassword");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword123", "oldEncodedPassword")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("newEncodedPassword");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("juanp");
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        ChangePasswordCommand command = new ChangePasswordCommand("OldPassword123", "NewPassword123");
        userService.changePassword(1L, command);

        assertEquals("newEncodedPassword", user.getPassword());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void changePassword_Self_IncorrectCurrentPassword_ThrowsException() {
        User user = new User();
        user.setId(1L);
        user.setUsername("juanp");
        user.setPassword("oldEncodedPassword");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "oldEncodedPassword")).thenReturn(false);

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("juanp");
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        ChangePasswordCommand command = new ChangePasswordCommand("WrongPassword", "NewPassword123");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.changePassword(1L, command));
        assertTrue(ex.getMessage().contains("incorrecta"));
    }

    @Test
    void changePassword_AdminReset_Success() {
        User user = new User();
        user.setId(1L);
        user.setUsername("juanp");
        user.setPassword("oldEncodedPassword");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("newEncodedPassword");

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("admin1");
        org.springframework.security.core.authority.SimpleGrantedAuthority authority =
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMINISTRATOR");
        doReturn(java.util.List.of(authority)).when(auth).getAuthorities();

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        ChangePasswordCommand command = new ChangePasswordCommand(null, "NewPassword123");
        userService.changePassword(1L, command);

        assertEquals("newEncodedPassword", user.getPassword());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void changePassword_SellerResetOther_ThrowsException() {
        User user = new User();
        user.setId(1L);
        user.setUsername("juanp");
        user.setPassword("oldEncodedPassword");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("seller1");
        org.springframework.security.core.authority.SimpleGrantedAuthority authority =
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SELLER");
        doReturn(java.util.List.of(authority)).when(auth).getAuthorities();

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        ChangePasswordCommand command = new ChangePasswordCommand(null, "NewPassword123");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> userService.changePassword(1L, command));
    }

    @Test
    void registerPendingAdmin_Success_CreatesUnverifiedAdminWithoutStore() {
        RegisterOwnerCommand command = new RegisterOwnerCommand("Carlos Martinez", "carlos.m", "carlos@empresa.com", "Password123");

        when(userRepository.existsByUsername("carlos.m")).thenReturn(false);
        when(userRepository.existsByEmail("carlos@empresa.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");

        User savedUser = new User();
        savedUser.setId(3L);
        savedUser.setRole("ADMINISTRATOR");
        savedUser.setEmailVerified(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserDto expectedDto = new UserDto(3L, "Carlos Martinez", "carlos.m", "carlos@empresa.com", "ADMINISTRATOR", true, null, false);
        when(userMapper.toDto(any(User.class))).thenReturn(expectedDto);

        UserDto result = userService.registerPendingAdmin(command);

        assertNotNull(result);
        assertFalse(result.emailVerified());
        assertNull(result.storeId());

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("ADMINISTRATOR", captor.getValue().getRole());
        assertFalse(captor.getValue().isEmailVerified());
        assertNull(captor.getValue().getStoreId());
    }

    @Test
    void registerPendingAdmin_DuplicateUsername_ThrowsException() {
        RegisterOwnerCommand command = new RegisterOwnerCommand("Carlos Martinez", "carlos.m", "carlos@empresa.com", "Password123");
        when(userRepository.existsByUsername("carlos.m")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.registerPendingAdmin(command));
        assertTrue(ex.getMessage().contains("usuario ya esta registrado"));
    }

    @Test
    void assignStore_UserAlreadyHasStore_ThrowsException() {
        User user = new User();
        user.setId(3L);
        user.setStoreId(1L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.assignStore(3L, 2L));
        assertTrue(ex.getMessage().contains("ya tiene un local"));
    }

    @Test
    void assignStore_Success() {
        User user = new User();
        user.setId(3L);
        user.setStoreId(null);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new UserDto(u.getId(), null, null, null, null, true, u.getStoreId(), true);
        });

        UserDto result = userService.assignStore(3L, 9L);

        assertEquals(9L, result.storeId());
    }

    @Test
    void markEmailVerified_Success() {
        User user = new User();
        user.setId(3L);
        user.setEmailVerified(false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new UserDto(u.getId(), null, null, null, null, true, u.getStoreId(), u.isEmailVerified());
        });

        UserDto result = userService.markEmailVerified(3L);

        assertTrue(result.emailVerified());
        assertTrue(user.isEmailVerified());
    }

    @Test
    void listActiveByStoreAndRoles_DelegatesToStoreScopedRepositoryQuery() {
        User admin = new User();
        admin.setId(1L);
        admin.setStoreId(5L);
        admin.setRole("ADMINISTRATOR");
        when(userRepository.findAllByStoreIdAndRoleInAndActiveTrue(5L, java.util.List.of("ADMINISTRATOR", "SUPERVISOR")))
                .thenReturn(java.util.List.of(admin));
        when(userMapper.toDto(admin)).thenReturn(new UserDto(1L, null, null, "ana@tienda.com", "ADMINISTRATOR", true, 5L, true));

        var result = userService.listActiveByStoreAndRoles(5L, java.util.List.of("ADMINISTRATOR", "SUPERVISOR"));

        assertEquals(1, result.size());
        assertEquals("ana@tienda.com", result.get(0).email());
    }

    // --- Aislamiento multi-tenant ---

    @Test
    void createUser_CallerFromStoreContext_IgnoresStoreIdFromCommandAndUsesOwnStore() {
        TenantContext.setStoreId(1L);
        // El comando intenta crear el usuario en el local 99 (otro local): debe ignorarse.
        CreateUserCommand command = new CreateUserCommand(
                "Juan Perez", "juanp", "juan@tienda.com", "Password123", "SELLER", 99L
        );
        when(userRepository.existsByUsername("juanp")).thenReturn(false);
        when(userRepository.existsByEmail("juan@tienda.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new UserDto(1L, u.getFullName(), u.getUsername(), u.getEmail(), u.getRole(), true, u.getStoreId(), true);
        });

        UserDto result = userService.createUser(command);

        assertEquals(1L, result.storeId());
        verify(userRepository).save(argThat(u -> u.getStoreId().equals(1L)));
    }

    @Test
    void createUser_CallerFromStoreContext_CannotCreateSuperAdmin() {
        TenantContext.setStoreId(1L);
        CreateUserCommand command = new CreateUserCommand(
                "Rogue Admin", "rogue", "rogue@tienda.com", "Password123", "SUPER_ADMIN", null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(command));
        assertTrue(ex.getMessage().contains("SUPER_ADMIN"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUser_AdministratorCannotPromoteToSuperAdmin() {
        TenantContext.setStoreId(1L);
        User seller = userOf(5L, "SELLER", 1L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(seller));

        UpdateUserCommand command = new UpdateUserCommand("Seller", "seller@tienda.com", "SUPER_ADMIN");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.updateUser(5L, command));
        assertTrue(ex.getMessage().contains("SUPER_ADMIN"));
        assertEquals("SELLER", seller.getRole());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUser_UserWithoutStoreCannotBecomeStoreRole() {
        User superAdmin = userOf(9L, "SUPER_ADMIN", null);
        when(userRepository.findById(9L)).thenReturn(Optional.of(superAdmin));

        UpdateUserCommand command = new UpdateUserCommand("Super", "sa@platform.com", "SELLER");

        assertThrows(IllegalArgumentException.class, () -> userService.updateUser(9L, command));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_SupervisorCannotResetAdministrator() {
        TenantContext.setStoreId(1L);
        User admin = userOf(2L, "ADMINISTRATOR", 1L);
        admin.setUsername("owner");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        authenticateAs("sup1", "ROLE_SUPERVISOR");

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> userService.changePassword(2L, new ChangePasswordCommand(null, "NewPassword123")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_SupervisorCanResetSeller() {
        TenantContext.setStoreId(1L);
        User seller = userOf(3L, "SELLER", 1L);
        seller.setUsername("vendedor");
        when(userRepository.findById(3L)).thenReturn(Optional.of(seller));
        when(passwordEncoder.encode("NewPassword123")).thenReturn("encoded");
        authenticateAs("sup1", "ROLE_SUPERVISOR");

        userService.changePassword(3L, new ChangePasswordCommand(null, "NewPassword123"));

        assertEquals("encoded", seller.getPassword());
    }

    @Test
    void changeStatus_ReactivatingTheOnlyAdministratorIsAllowed() {
        TenantContext.setStoreId(1L);
        User admin = userOf(2L, "ADMINISTRATOR", 1L);
        admin.setUsername("owner");
        admin.setActive(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(userRepository.countByStoreIdAndRoleAndActiveTrue(1L, "ADMINISTRATOR")).thenReturn(0L);
        authenticateAs("other", "ROLE_ADMINISTRATOR");

        userService.changeStatus(2L, true);

        assertTrue(admin.isActive());
    }

    private User userOf(Long id, String role, Long storeId) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStoreId(storeId);
        user.setEmail("u" + id + "@tienda.com");
        user.setPassword("old");
        return user;
    }

    private void authenticateAs(String username, String authority) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        doReturn(java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(authority)))
                .when(auth).getAuthorities();
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    void getById_UserFromAnotherStore_ThrowsNotFound() {
        TenantContext.setStoreId(1L);
        User otherStoreUser = new User();
        otherStoreUser.setId(7L);
        otherStoreUser.setStoreId(2L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(otherStoreUser));

        assertThrows(EntityNotFoundException.class, () -> userService.getById(7L));
    }

    @Test
    void getById_UserFromOwnStore_Succeeds() {
        TenantContext.setStoreId(1L);
        User ownStoreUser = new User();
        ownStoreUser.setId(7L);
        ownStoreUser.setStoreId(1L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(ownStoreUser));
        when(userMapper.toDto(ownStoreUser)).thenReturn(new UserDto(7L, "Ana", "ana", "ana@a.com", "SELLER", true, 1L, true));

        UserDto result = userService.getById(7L);

        assertEquals(7L, result.id());
    }

    @Test
    void listUsers_CallerFromStoreContext_UsesStoreScopedQuery() {
        TenantContext.setStoreId(1L);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        when(userRepository.findAllByStoreIdAndActiveTrue(eq(1L), any())).thenReturn(org.springframework.data.domain.Page.empty());

        userService.listUsers(pageable);

        verify(userRepository, times(1)).findAllByStoreIdAndActiveTrue(eq(1L), any());
        verify(userRepository, never()).findAllByActiveTrue(any());
    }
}