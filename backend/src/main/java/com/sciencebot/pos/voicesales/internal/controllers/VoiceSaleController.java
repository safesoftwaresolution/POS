package com.sciencebot.pos.voicesales.internal.controllers;

import com.sciencebot.pos.voicesales.VoiceSaleFacade;
import com.sciencebot.pos.voicesales.VoiceSalePreviewDto;
import com.sciencebot.pos.voicesales.internal.ports.VoiceProviderException;
import com.sciencebot.pos.voicesales.internal.services.PreviewRejected;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/voice-sales")
@ConditionalOnProperty(name = "voice-sales.enabled", havingValue = "true")
class VoiceSaleController {
    private static final Logger log = LoggerFactory.getLogger(VoiceSaleController.class);
    private final VoiceSaleFacade voiceSales;

    VoiceSaleController(VoiceSaleFacade voiceSales) {
        this.voiceSales = voiceSales;
    }

    @PostMapping(value = "/preview", produces = "application/json")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','SUPERVISOR','SELLER')")
    @Operation(summary = "Preparar un borrador de venta por voz, sin registrar ni cobrar",
            description = "Revisa las opciones y confirma por separado con POST /api/v1/sales.",
            requestBody = @RequestBody(required = true, description = "Audio binario, máximo 5 MiB; no multipart ni URL.",
                    content = {
                            @Content(mediaType = "audio/webm", schema = @Schema(type = "string", format = "binary")),
                            @Content(mediaType = "audio/wav", schema = @Schema(type = "string", format = "binary"))}),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Borrador editable", useReturnTypeSchema = true),
                    @ApiResponse(responseCode = "403", description = "Rol o tienda no autorizados"),
                    @ApiResponse(responseCode = "413", description = "Audio demasiado grande"),
                    @ApiResponse(responseCode = "415", description = "Formato no compatible"),
                    @ApiResponse(responseCode = "429", description = "Límite de usuario o tienda"),
                    @ApiResponse(responseCode = "503", description = "Proveedor no disponible; se puede usar la venta manual")})
    public ResponseEntity<VoiceSalePreviewDto> preview(HttpServletRequest request) throws IOException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                voiceSales.preview(request.getInputStream(), request.getContentType(), request.getContentLengthLong()));
    }

    @ExceptionHandler(PreviewRejected.class)
    ResponseEntity<Map<String, String>> rejected(PreviewRejected ex) {
        return error(ex.status(), ex.getMessage());
    }

    @ExceptionHandler(VoiceProviderException.class)
    ResponseEntity<Map<String, String>> unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo interpretar el audio. Puedes registrar la venta manualmente.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden() {
        return error(HttpStatus.FORBIDDEN, "No se permite el dictado para esta sesión.");
    }

    // The global fallback includes the exception message; never reflect or log private mentions.
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception failure) {
        log.error("Voice preview failed unexpectedly: {}", failure.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar el borrador.");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }
}
