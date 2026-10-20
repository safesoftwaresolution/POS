package com.sciencebot.pos.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SupportTicketFacade {
    SupportTicketDto create(CreateTicketCommand command);
    SupportTicketDto trackByNumber(String ticketNumber);
    Page<SupportTicketDto> search(String type, String status, String priority, Long storeId, String q, Pageable pageable);
    SupportTicketDto getById(Long id);
    SupportTicketDto update(Long id, UpdateTicketCommand command);
    SupportMetricsDto getMetrics();
}