package com.sciencebot.pos.appointments.internal.controllers;

import com.sciencebot.pos.appointments.AppointmentDto;
import com.sciencebot.pos.appointments.AppointmentFacade;
import com.sciencebot.pos.appointments.CreateAppointmentCommand;
import com.sciencebot.pos.appointments.RescheduleAppointmentCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "📅 Citas", description = "Calendario y agendamiento de citas (locales con plantilla de citas)")
public class AppointmentController {

    private final AppointmentFacade appointmentFacade;

    public AppointmentController(AppointmentFacade appointmentFacade) {
        this.appointmentFacade = appointmentFacade;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Listar citas de un rango",
            description = "Citas cuyo inicio cae en [from, to), ordenadas por inicio. Rango máximo de 62 días.")
    public ResponseEntity<List<AppointmentDto>> list(
            @Parameter(description = "Inicio del rango (inclusive)", example = "2026-11-09T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Fin del rango (exclusivo)", example = "2026-11-16T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(appointmentFacade.listBetween(from, to));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Agendar una cita",
            description = "Cliente, servicio e inicio son obligatorios. El fin se calcula con la duración del servicio (30 min si no la tiene).")
    public ResponseEntity<AppointmentDto> create(@RequestBody CreateAppointmentCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentFacade.create(command));
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Mover una cita", description = "Cambia el inicio conservando la duración. Una cita cancelada no se puede mover.")
    public ResponseEntity<AppointmentDto> reschedule(@PathVariable Long id, @RequestBody RescheduleAppointmentCommand command) {
        return ResponseEntity.ok(appointmentFacade.reschedule(id, command));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    @Operation(summary = "Cancelar una cita")
    public ResponseEntity<AppointmentDto> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentFacade.cancel(id));
    }
}
