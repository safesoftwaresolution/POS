package com.sciencebot.pos.voicesales.internal.controllers;

import com.sciencebot.pos.voicesales.VoiceAgentFacade;
import com.sciencebot.pos.voicesales.VoiceAgentRequest;
import com.sciencebot.pos.voicesales.VoiceAgentResponse;
import com.sciencebot.pos.voicesales.internal.ports.VoiceProviderException;
import com.sciencebot.pos.voicesales.internal.services.PreviewRejected;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/** Conversational voice/text assistant for the POS ticket. It edits a ticket; it never registers a sale. */
@RestController
@RequestMapping("/api/v1/voice-sales/agent")
@ConditionalOnProperty(name = {"voice-sales.enabled", "voice-sales.agent.enabled"}, havingValue = "true")
class VoiceAgentController {
    private static final Logger log = LoggerFactory.getLogger(VoiceAgentController.class);
    // Generous upper bounds on the JSON a client may send; the service trims history and ticket further.
    static final int MAX_HISTORY_ITEMS = 50;
    static final int MAX_TICKET_LINES = 100;
    private final VoiceAgentFacade agent;

    VoiceAgentController(VoiceAgentFacade agent) {
        this.agent = agent;
    }

    @PostMapping(value = "/transcribe", produces = "application/json")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','SUPERVISOR','SELLER')")
    @Operation(summary = "Pasar un audio a texto, sin guardarlo",
            description = "Audio binario WebM o WAV, máximo 5 MiB; no multipart. Devuelve solo el texto.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Texto transcrito"),
                    @ApiResponse(responseCode = "403", description = "Rol o tienda no autorizados"),
                    @ApiResponse(responseCode = "413", description = "Audio demasiado grande"),
                    @ApiResponse(responseCode = "415", description = "Formato no compatible"),
                    @ApiResponse(responseCode = "429", description = "Límite de usuario o tienda"),
                    @ApiResponse(responseCode = "503", description = "Proveedor no disponible; se puede usar la venta manual")})
    public ResponseEntity<Map<String, String>> transcribe(HttpServletRequest request) throws IOException {
        String text = agent.transcribe(request.getInputStream(), request.getContentType(), request.getContentLengthLong());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("text", text));
    }

    @PostMapping(value = "/turn", consumes = "application/json", produces = "application/json")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','SUPERVISOR','SELLER')")
    @Operation(summary = "Aplicar lo que dijo el cajero al ticket, sin registrar ni cobrar",
            description = "Recibe el texto, el ticket actual (solo ids y cantidades) y un historial corto. Devuelve la "
                    + "respuesta del asistente y el ticket resultante. Si `ticket.confirmationRequested` es true, el POS "
                    + "muestra su confirmación; el cobro se hace por separado con POST /api/v1/sales.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Ticket actualizado", useReturnTypeSchema = true),
                    @ApiResponse(responseCode = "400", description = "Texto vacío o demasiado largo"),
                    @ApiResponse(responseCode = "403", description = "Rol o tienda no autorizados"),
                    @ApiResponse(responseCode = "413", description = "Ticket o historial demasiado grandes"),
                    @ApiResponse(responseCode = "429", description = "Límite de usuario o tienda"),
                    @ApiResponse(responseCode = "503", description = "Proveedor no disponible; se puede usar la venta manual")})
    public ResponseEntity<VoiceAgentResponse> turn(@RequestBody VoiceAgentRequest request) {
        if (request != null && ((request.history() != null && request.history().size() > MAX_HISTORY_ITEMS)
                || (request.ticket() != null && request.ticket().lines() != null
                && request.ticket().lines().size() > MAX_TICKET_LINES))) {
            throw new PreviewRejected(HttpStatus.PAYLOAD_TOO_LARGE, "El ticket o el historial son demasiado grandes.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(agent.turn(request));
    }

    @ExceptionHandler(PreviewRejected.class)
    ResponseEntity<Map<String, String>> rejected(PreviewRejected ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(VoiceProviderException.class)
    ResponseEntity<Map<String, String>> unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE,
                "El asistente no está disponible ahora. Puedes seguir con la venta manual.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden() {
        return error(HttpStatus.FORBIDDEN, "No se permite el asistente para esta sesión.");
    }

    // A body that is not valid JSON (or not valid UTF-8) is the caller's mistake, not a server failure. The parser's
    // message can quote what was sent, so it is never reflected.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable() {
        return error(HttpStatus.BAD_REQUEST, "La solicitud no es válida.");
    }

    // The global fallback includes the exception message; never reflect or log what the cashier said.
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception failure) {
        log.error("Voice agent failed unexpectedly: {}", failure.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo procesar el pedido.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }
}
