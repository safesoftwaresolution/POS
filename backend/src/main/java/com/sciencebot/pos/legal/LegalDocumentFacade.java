package com.sciencebot.pos.legal;

import java.util.List;

public interface LegalDocumentFacade {
    LegalDocumentDto getPublished(String slug);
    List<LegalDocumentDto> listAll();
    LegalDocumentDto getById(Long id);
    LegalDocumentDto create(SaveLegalDocumentCommand command);
    LegalDocumentDto update(Long id, SaveLegalDocumentCommand command);
    void delete(Long id);
    LegalDocumentDto togglePublish(Long id);
}