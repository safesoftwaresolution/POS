package com.sciencebot.pos.legal.internal.repositories;

import com.sciencebot.pos.legal.internal.entities.LegalDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LegalDocumentRepository extends JpaRepository<LegalDocumentEntity, Long> {
    Optional<LegalDocumentEntity> findBySlugAndPublishedTrue(String slug);
    boolean existsBySlug(String slug);
}