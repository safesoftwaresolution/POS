package com.sciencebot.pos.legal;

import com.sciencebot.pos.legal.internal.entities.LegalDocumentEntity;
import com.sciencebot.pos.legal.internal.mappers.LegalDocumentMapper;
import com.sciencebot.pos.legal.internal.repositories.LegalDocumentRepository;
import com.sciencebot.pos.legal.internal.services.LegalDocumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LegalDocumentServiceImplTest {

    @Mock private LegalDocumentRepository repository;
    @Mock private LegalDocumentMapper mapper;
    @InjectMocks private LegalDocumentServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getPublished_Success() {
        LegalDocumentEntity entity = new LegalDocumentEntity();
        entity.setSlug("privacy-policy");
        entity.setPublished(true);

        when(repository.findBySlugAndPublishedTrue("privacy-policy")).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(new LegalDocumentDto(1L, "privacy-policy", "Politicas", "Contenido", "1.0", true, null, null, null));

        LegalDocumentDto result = service.getPublished("privacy-policy");
        assertNotNull(result);
        assertEquals("privacy-policy", result.slug());
        assertTrue(result.published());
    }

    @Test
    void create_DuplicateSlug_ThrowsException() {
        when(repository.existsBySlug("terms")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(new SaveLegalDocumentCommand("terms", "Terminos", "Contenido", "1.0")));
    }

    @Test
    void togglePublish_Success() {
        LegalDocumentEntity entity = new LegalDocumentEntity();
        entity.setId(1L);
        entity.setPublished(false);

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenReturn(entity);
        when(mapper.toDto(any())).thenReturn(new LegalDocumentDto(1L, "slug", "Title", "Text", "1.0", true, null, null, null));

        LegalDocumentDto result = service.togglePublish(1L);
        assertNotNull(result);
        assertTrue(entity.isPublished());
    }
}