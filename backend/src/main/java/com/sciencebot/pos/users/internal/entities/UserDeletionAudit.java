package com.sciencebot.pos.users.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro inmutable de un borrado definitivo de usuario. Copia los datos del usuario porque la fila
 * original ya no existe. Solo se inserta; nunca se actualiza ni se elimina. Sin datos sensibles.
 */
@Entity
@Table(name = "user_deletion_audit")
@Getter
@Setter
public class UserDeletionAudit {

    public static final String KIND_PERMANENT = "PERMANENT";
    public static final String KIND_UNVERIFIED = "UNVERIFIED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "username", nullable = false, length = 50, updatable = false)
    private String username;

    @Column(name = "full_name", length = 100, updatable = false)
    private String fullName;

    @Column(name = "email", length = 100, updatable = false)
    private String email;

    @Column(name = "role", length = 30, updatable = false)
    private String role;

    @Column(name = "store_id", updatable = false)
    private Long storeId;

    /** PERMANENT (borrado definitivo desde el directorio) o UNVERIFIED (cuenta pendiente sin local). */
    @Column(name = "kind", nullable = false, length = 20, updatable = false)
    private String kind;

    @Column(name = "performed_by", length = 100, updatable = false)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
