package com.sciencebot.pos.notifications.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter @Setter
public class NotificationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_id", nullable = false)
    private Long storeId;
    @Column(nullable = false, length = 40)
    private String type;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(nullable = false, length = 500)
    private String message;
    @Column(name = "link_path", length = 255)
    private String linkPath;
    @Column(nullable = false)
    private boolean read = false;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
