package com.sciencebot.pos.stores.internal.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_documents")
@Getter @Setter
public class StoreDocumentEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_id", nullable = false)
    private Long storeId;
    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;
    @Column(name = "document_url", nullable = false, length = 500)
    private String documentUrl;
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;
    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    @PrePersist protected void onCreate() { uploadedAt = LocalDateTime.now(); }
}