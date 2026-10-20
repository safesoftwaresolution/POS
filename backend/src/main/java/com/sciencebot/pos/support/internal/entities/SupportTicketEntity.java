package com.sciencebot.pos.support.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "support_tickets")
@Getter @Setter
public class SupportTicketEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "ticket_number", nullable = false, unique = true, length = 30)
    private String ticketNumber;
    @Column(nullable = false, length = 20)
    private String type;
    @Column(nullable = false, length = 10)
    private String priority = "MEDIUM";
    @Column(nullable = false, length = 15)
    private String status = "OPEN";
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;
    @Column(name = "contact_email", nullable = false, length = 100)
    private String contactEmail;
    @Column(name = "contact_phone", length = 20)
    private String contactPhone;
    @Column(name = "store_id")
    private Long storeId;
    @Column(name = "system_info", columnDefinition = "TEXT")
    private String systemInfo;
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}