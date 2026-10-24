package com.sciencebot.pos.stores.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Plan de suscripción: define cuántos usuarios adicionales (además del administrador) puede tener un local. */
@Entity
@Table(name = "plans")
@Getter
@Setter
public class PlanEntity {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "max_additional_users", nullable = false)
    private int maxAdditionalUsers;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
