package com.sciencebot.pos.support.internal.services;

import com.sciencebot.pos.support.*;
import com.sciencebot.pos.support.internal.entities.SupportTicketEntity;
import com.sciencebot.pos.support.internal.mappers.SupportTicketMapper;
import com.sciencebot.pos.support.internal.repositories.SupportTicketRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Transactional(readOnly = true)
public class SupportTicketServiceImpl implements SupportTicketFacade {

    private static final Set<String> VALID_TYPES     = Set.of("PETITION","COMPLAINT","CLAIM","SUGGESTION","BUG_REPORT");
    private static final Set<String> VALID_PRIORITIES = Set.of("LOW","MEDIUM","HIGH","CRITICAL");
    private static final Set<String> VALID_STATUSES   = Set.of("OPEN","IN_PROGRESS","RESOLVED","CLOSED");

    private final SupportTicketRepository repository;
    private final SupportTicketMapper mapper;
    private final AtomicLong dailyCounter = new AtomicLong(0);
    private volatile String lastDate = "";

    public SupportTicketServiceImpl(SupportTicketRepository repository, SupportTicketMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public SupportTicketDto create(CreateTicketCommand command) {
        if (!VALID_TYPES.contains(command.type().toUpperCase())) {
            throw new IllegalArgumentException("Tipo de ticket invalido: " + command.type());
        }
        SupportTicketEntity entity = new SupportTicketEntity();
        entity.setTicketNumber(generateTicketNumber(command.type()));
        entity.setType(command.type().toUpperCase());
        entity.setPriority(command.priority() != null ? command.priority().toUpperCase() : "MEDIUM");
        entity.setStatus("OPEN");
        entity.setTitle(command.title());
        entity.setDescription(command.description());
        entity.setContactName(command.contactName());
        entity.setContactEmail(command.contactEmail());
        entity.setContactPhone(command.contactPhone());
        entity.setStoreId(command.storeId());
        entity.setSystemInfo(command.systemInfo());
        return mapper.toDto(repository.save(entity));
    }

    @Override
    public SupportTicketDto trackByNumber(String ticketNumber) {
        return repository.findByTicketNumber(ticketNumber)
                .map(mapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Ticket no encontrado: " + ticketNumber));
    }

    @Override
    public Page<SupportTicketDto> search(String type, String status, String priority, Long storeId, String q, Pageable pageable) {
        String t = (type == null || type.isBlank()) ? null : type.toUpperCase();
        String s = (status == null || status.isBlank()) ? null : status.toUpperCase();
        String p = (priority == null || priority.isBlank()) ? null : priority.toUpperCase();
        String qp = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        return repository.searchTickets(t, s, p, storeId, qp, pageable).map(mapper::toDto);
    }

    @Override
    public SupportTicketDto getById(Long id) {
        return repository.findById(id).map(mapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Ticket no encontrado con ID: " + id));
    }

    @Override
    @Transactional
    public SupportTicketDto update(Long id, UpdateTicketCommand command) {
        SupportTicketEntity entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Ticket no encontrado con ID: " + id));
        if (command.status() != null) {
            if (!VALID_STATUSES.contains(command.status().toUpperCase())) {
                throw new IllegalArgumentException("Estado invalido: " + command.status());
            }
            entity.setStatus(command.status().toUpperCase());
            if ("RESOLVED".equals(entity.getStatus()) || "CLOSED".equals(entity.getStatus())) {
                entity.setResolvedAt(LocalDateTime.now());
            }
        }
        if (command.priority() != null) {
            if (!VALID_PRIORITIES.contains(command.priority().toUpperCase())) {
                throw new IllegalArgumentException("Prioridad invalida: " + command.priority());
            }
            entity.setPriority(command.priority().toUpperCase());
        }
        if (command.resolutionNotes() != null) {
            entity.setResolutionNotes(command.resolutionNotes());
        }
        return mapper.toDto(repository.save(entity));
    }

    @Override
    public SupportMetricsDto getMetrics() {
        return new SupportMetricsDto(
                repository.countByStatus("OPEN"),
                repository.countByStatus("IN_PROGRESS"),
                repository.countByStatus("RESOLVED"),
                repository.countByStatus("CLOSED"),
                repository.countByTypeAndPriority("BUG_REPORT", "CRITICAL"),
                repository.count()
        );
    }

    private synchronized String generateTicketNumber(String type) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (!today.equals(lastDate)) {
            dailyCounter.set(0);
            lastDate = today;
        }
        String prefix = "BUG_REPORT".equals(type.toUpperCase()) ? "BUG" : "PQR";
        return String.format("%s-%s-%04d", prefix, today, dailyCounter.incrementAndGet());
    }
}