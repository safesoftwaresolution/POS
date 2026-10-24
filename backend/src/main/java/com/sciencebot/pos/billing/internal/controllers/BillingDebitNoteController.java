package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.dto.CreateDebitNoteRequest;
import com.sciencebot.pos.billing.dto.DebitNoteDTO;
import com.sciencebot.pos.billing.internal.controllers.doc.BillingDebitNoteApiDoc;
import com.sciencebot.pos.billing.internal.services.DebitNoteService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/debit-notes")
public class BillingDebitNoteController implements BillingDebitNoteApiDoc {

    private final DebitNoteService debitNoteService;

    public BillingDebitNoteController(DebitNoteService debitNoteService) {
        this.debitNoteService = debitNoteService;
    }

    @Override
    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<DebitNoteDTO> createAndValidateDebitNote(@Valid @RequestBody CreateDebitNoteRequest request) {
        return ResponseEntity.ok(debitNoteService.createAndValidateDebitNote(request));
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<Page<DebitNoteDTO>> listDebitNotes(
            Pageable pageable,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(debitNoteService.listDebitNotes(pageable, status));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<DebitNoteDTO> getDebitNoteById(@PathVariable Long id) {
        return debitNoteService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @GetMapping("/reference/{referenceCode}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<DebitNoteDTO> getDebitNoteByReferenceCode(@PathVariable String referenceCode) {
        return debitNoteService.getByReferenceCode(referenceCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = debitNoteService.downloadDebitNotePDF(id);
        if (pdf == null || pdf.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"nota-debito-" + id + ".pdf\"")
                .body(pdf);
    }

    @Override
    @GetMapping("/{id}/xml")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<byte[]> downloadXml(@PathVariable Long id) {
        byte[] xml = debitNoteService.downloadDebitNoteXML(id);
        if (xml == null || xml.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nota-debito-" + id + ".xml\"")
                .body(xml);
    }

    @Override
    @DeleteMapping("/reference/{referenceCode}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<Void> deleteUnvalidatedDebitNote(@PathVariable String referenceCode) {
        if (debitNoteService.deleteUnvalidatedDebitNote(referenceCode)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @Override
    @PostMapping("/sales/{saleId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<DebitNoteDTO> createForSale(
            @PathVariable Long saleId,
            @Valid @RequestBody CreateDebitNoteRequest request) {
        return ResponseEntity.ok(debitNoteService.createForSale(saleId, request));
    }
}
