package com.sciencebot.pos.settings.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "settings")
@Getter
@Setter
public class Setting {

    /**
     * Llave primaria compartida con el local (tenant) dueño de esta configuracion: id = id del
     * local en la tabla stores (relacion 1 a 1). No es autogenerada: el servicio la asigna
     * explicitamente a partir de TenantContext.getStoreId() al crear la fila de un local nuevo.
     * Antes de esto, 'settings' era un singleton global (id fijo = 1) compartido por todos los
     * locales.
     */
    @Id
    private Long id;

    @Column(name = "business_name", nullable = false, length = 100)
    private String businessName;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(length = 100)
    private String email;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
