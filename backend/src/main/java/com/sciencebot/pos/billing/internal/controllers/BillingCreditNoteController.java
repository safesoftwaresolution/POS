package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.dto.CreateCreditNoteRequest;
import com.sciencebot.pos.billing.dto.CreditNoteDTO;
import com.sciencebot.pos.billing.internal.controllers.doc.BillingCreditNoteApiDoc;
import com.sciencebot.pos.billing.internal.services.CreditNoteService;
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
@RequestMapping("/api/v1/billing/credit-notes")
public class BillingCreditNoteController implements BillingCreditNoteApiDoc {

    private final CreditNoteService creditNoteService;

    public BillingCreditNoteController(CreditNoteService creditNoteService) {
        this.creditNoteService = creditNoteService;
    }

    @Override
    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<CreditNoteDTO> createAndValidateCreditNote(@Valid @RequestBody CreateCreditNoteRequest request) {
        CreditNoteDTO result = creditNoteService.createAndValidateCreditNote(request);
        return ResponseEntity.ok(result);
    }

    @Override
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<Page<CreditNoteDTO>> listCreditNotes(
            Pageable pageable,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(creditNoteService.listCreditNotes(pageable, status));
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<CreditNoteDTO> getCreditNoteById(@PathVariable Long id) {
        return creditNoteService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @GetMapping("/reference/{referenceCode}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<CreditNoteDTO> getCreditNoteByReferenceCode(@PathVariable String referenceCode) {
        return creditNoteService.getByReferenceCode(referenceCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] pdf = creditNoteService.downloadCreditNotePDF(id);
        if (pdf == null || pdf.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"nota-credito-" + id + ".pdf\"")
                .body(pdf);
    }

    @Override
    @GetMapping("/{id}/xml")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<byte[]> downloadXml(@PathVariable Long id) {
        byte[] xml = creditNoteService.downloadCreditNoteXML(id);
        if (xml == null || xml.length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nota-credito-" + id + ".xml\"")
                .body(xml);
    }

    @Override
    @DeleteMapping("/reference/{referenceCode}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<Void> deleteUnvalidatedCreditNote(@PathVariable String referenceCode) {
        boolean deleted = creditNoteService.deleteUnvalidatedCreditNote(referenceCode);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @Override
    @PostMapping("/sales/{saleId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<CreditNoteDTO> createFromSale(
            @PathVariable Long saleId,
            @RequestParam(required = false, defaultValue = "2") String conceptCode,
            @RequestParam(required = false) String observation) {
        CreditNoteDTO result = creditNoteService.createFromSale(saleId, conceptCode, observation);
        return ResponseEntity.ok(result);
    }
}
