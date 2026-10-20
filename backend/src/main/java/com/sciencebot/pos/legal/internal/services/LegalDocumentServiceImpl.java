package com.sciencebot.pos.legal.internal.services;

import com.sciencebot.pos.legal.*;
import com.sciencebot.pos.legal.internal.entities.LegalDocumentEntity;
import com.sciencebot.pos.legal.internal.mappers.LegalDocumentMapper;
import com.sciencebot.pos.legal.internal.repositories.LegalDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class LegalDocumentServiceImpl implements LegalDocumentFacade {

    private final LegalDocumentRepository repository;
    private final LegalDocumentMapper mapper;

    public LegalDocumentServiceImpl(LegalDocumentRepository repository, LegalDocumentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public LegalDocumentDto getPublished(String slug) {
        return repository.findBySlugAndPublishedTrue(slug)
                .map(mapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Texto legal no encontrado o no publicado: " + slug));
    }

    @Override
    public List<LegalDocumentDto> listAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public LegalDocumentDto getById(Long id) {
        return repository.findById(id).map(mapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Documento legal no encontrado con ID: " + id));
    }

    @Override
    @Transactional
    public LegalDocumentDto create(SaveLegalDocumentCommand command) {
        if (repository.existsBySlug(command.slug())) {
            throw new IllegalArgumentException("Ya existe un documento con el slug: " + command.slug());
        }
        LegalDocumentEntity entity = new LegalDocumentEntity();
        entity.setSlug(command.slug());
        entity.setTitle(command.title());
        entity.setContent(command.content());
        entity.setVersion(command.version() != null ? command.version() : "1.0");
        entity.setPublished(false);
        return mapper.toDto(repository.save(entity));
    }

    @Override
    @Transactional
    public LegalDocumentDto update(Long id, SaveLegalDocumentCommand command) {
        LegalDocumentEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Documento legal no encontrado con ID: " + id));
        entity.setTitle(command.title());
        entity.setContent(command.content());
        if (command.version() != null) entity.setVersion(command.version());
        return mapper.toDto(repository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        LegalDocumentEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Documento legal no encontrado con ID: " + id));
        if (entity.isPublished()) {
            throw new IllegalStateException("No se puede eliminar un documento publicado. Despublicalo primero.");
        }
        repository.delete(entity);
    }

    @Override
    @Transactional
    public LegalDocumentDto togglePublish(Long id) {
        LegalDocumentEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Documento legal no encontrado con ID: " + id));
        entity.setPublished(!entity.isPublished());
        entity.setPublishedAt(entity.isPublished() ? LocalDateTime.now() : null);
        return mapper.toDto(repository.save(entity));
    }
}