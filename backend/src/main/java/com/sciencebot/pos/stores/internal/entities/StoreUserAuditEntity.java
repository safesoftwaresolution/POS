package com.sciencebot.pos.stores.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro inmutable de una asignación de usuario a local hecha desde el backoffice. Guarda una copia
 * de los datos del local y del usuario, así que sigue siendo legible aunque alguno se elimine.
 */
@Entity
@Table(name = "store_user_audit")
@Getter
@Setter
public class StoreUserAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "store_name", length = 100, updatable = false)
    private String storeName;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "username", length = 50, updatable = false)
    private String username;

    @Column(name = "full_name", length = 100, updatable = false)
    private String fullName;

    @Column(name = "role", length = 20, updatable = false)
    private String role;

    @Column(name = "action", nullable = false, length = 20, updatable = false)
    private String action; // OWNER_ASSIGNED

    @Column(name = "performed_by", length = 100, updatable = false)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
