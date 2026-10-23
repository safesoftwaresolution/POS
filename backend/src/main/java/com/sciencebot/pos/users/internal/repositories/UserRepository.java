package com.sciencebot.pos.users.internal.repositories;

import com.sciencebot.pos.users.internal.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Usuario y correo se comparan sin distinguir mayusculas: el login normaliza a minusculas lo que escribe la
    // persona, asi que un usuario registrado como "AndresV" nunca coincidiria con una busqueda exacta.
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsername(@Param("username") String username);
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(@Param("email") String email);
    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsername(@Param("username") String username);
    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);
    long countByRoleAndActiveTrue(String role);
    Page<User> findAllByActiveTrue(Pageable pageable);
    Page<User> findAllByRole(String role, Pageable pageable);
    Page<User> findAllByRoleAndEmailVerifiedFalse(String role, Pageable pageable);

    Page<User> findAllByRoleAndEmailVerifiedTrue(String role, Pageable pageable);
    List<User> findAllByStoreIdAndRoleInAndActiveTrue(Long storeId, List<String> roles);
    Page<User> findAllByStoreIdAndActiveTrue(Long storeId, Pageable pageable);
    Page<User> findAllByStoreIdAndRole(Long storeId, String role, Pageable pageable);
    long countByStoreIdAndRoleAndActiveTrue(Long storeId, String role);
    long countByStoreIdAndActiveTrue(Long storeId);
}
