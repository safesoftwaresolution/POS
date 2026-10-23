package com.sciencebot.pos.support;

import com.sciencebot.pos.support.internal.entities.SupportTicketEntity;
import com.sciencebot.pos.support.internal.mappers.SupportTicketMapper;
import com.sciencebot.pos.support.internal.repositories.SupportTicketRepository;
import com.sciencebot.pos.support.internal.services.SupportTicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupportTicketServiceImplTest {

    @Mock private SupportTicketRepository repository;
    @Mock private SupportTicketMapper mapper;
    @InjectMocks private SupportTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void create_GeneratesTicketNumberAndSaves() {
        CreateTicketCommand command = new CreateTicketCommand(
                "BUG_REPORT", "CRITICAL", "Error en checkout", "Falla al pagar",
                "Juan Admin", "juan@tienda.com", "3001234567", 1L, "Chrome 120"
        );

        when(repository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> {
            SupportTicketEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        when(mapper.toDto(any())).thenAnswer(inv -> {
            SupportTicketEntity e = inv.getArgument(0);
            return new SupportTicketDto(e.getId(), e.getTicketNumber(), e.getType(), e.getPriority(), e.getStatus(), e.getTitle(), e.getDescription(), e.getContactName(), e.getContactEmail(), e.getContactPhone(), e.getStoreId(), e.getSystemInfo(), e.getResolutionNotes(), e.getCreatedAt(), e.getUpdatedAt(), e.getResolvedAt());
        });

        SupportTicketDto result = service.create(command);
        assertNotNull(result);
        assertTrue(result.ticketNumber().startsWith("BUG-"));
        assertEquals("BUG_REPORT", result.type());
        assertEquals("CRITICAL", result.priority());
    }

    @Test
    void trackByNumber_Success() {
        SupportTicketEntity entity = new SupportTicketEntity();
        entity.setTicketNumber("PQR-20260819-0001");

        when(repository.findByTicketNumber("PQR-20260819-0001")).thenReturn(Optional.of(entity));
        when(mapper.toTrackingDto(entity)).thenReturn(new TicketTrackingDto("PQR-20260819-0001", "PETITION", "MEDIUM", "OPEN", "Titulo", null, null, null));

        TicketTrackingDto result = service.trackByNumber("PQR-20260819-0001");
        assertNotNull(result);
        assertEquals("PQR-20260819-0001", result.ticketNumber());
    }

    @Test
    void trackingDto_DoesNotExposeContactData() {
        SupportTicketEntity entity = new SupportTicketEntity();
        entity.setTicketNumber("PQR-20260819-AB3K9X");
        entity.setContactEmail("privado@tienda.com");
        entity.setContactPhone("3001234567");
        entity.setDescription("detalle interno");

        TicketTrackingDto dto = new com.sciencebot.pos.support.internal.mappers.SupportTicketMapper().toTrackingDto(entity);

        assertEquals("PQR-20260819-AB3K9X", dto.ticketNumber());
        assertFalse(java.util.Arrays.stream(TicketTrackingDto.class.getRecordComponents())
                .anyMatch(c -> c.getName().toLowerCase().contains("contact") || c.getName().equals("description")));
    }

    @Test
    void create_TicketNumberIsRandomSuffixed() {
        CreateTicketCommand command = new CreateTicketCommand(
                "PETITION", null, "Consulta", "Detalle", "Ana", "ana@tienda.com", null, 1L, null);
        when(repository.save(any(SupportTicketEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDto(any())).thenAnswer(inv -> {
            SupportTicketEntity e = inv.getArgument(0);
            return new SupportTicketDto(1L, e.getTicketNumber(), e.getType(), e.getPriority(), e.getStatus(), e.getTitle(), e.getDescription(), e.getContactName(), e.getContactEmail(), e.getContactPhone(), e.getStoreId(), e.getSystemInfo(), e.getResolutionNotes(), e.getCreatedAt(), e.getUpdatedAt(), e.getResolvedAt());
        });

        SupportTicketDto first = service.create(command);

        assertTrue(first.ticketNumber().matches("PQR-\\d{8}-[A-Z2-9]{6}"));
    }

    @Test
    void update_StatusResolved_SetsResolvedAt() {
        SupportTicketEntity entity = new SupportTicketEntity();
        entity.setId(1L);
        entity.setStatus("OPEN");

        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenReturn(entity);
        when(mapper.toDto(any())).thenReturn(new SupportTicketDto(1L, "TICK-1", "PETITION", "MEDIUM", "RESOLVED", "Titulo", "Desc", "Name", "email@test.com", null, null, null, "Solucionado", null, null, null));

        SupportTicketDto result = service.update(1L, new UpdateTicketCommand("RESOLVED", "MEDIUM", "Solucionado"));
        assertNotNull(result);
        assertEquals("RESOLVED", entity.getStatus());
        assertNotNull(entity.getResolvedAt());
    }
}