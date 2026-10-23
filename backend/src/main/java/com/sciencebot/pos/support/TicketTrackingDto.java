package com.sciencebot.pos.support;

import java.time.LocalDateTime;

/**
 * Vista publica del seguimiento de un radicado: solo estado y resolucion. No incluye datos de
 * contacto, descripcion ni informacion del sistema, porque el endpoint no esta autenticado.
 */
public record TicketTrackingDto(
    String ticketNumber, String type, String priority, String status, String title,
    String resolutionNotes, LocalDateTime createdAt, LocalDateTime resolvedAt
) {}
