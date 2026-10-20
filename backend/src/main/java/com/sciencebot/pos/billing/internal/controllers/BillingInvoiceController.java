package com.sciencebot.pos.billing.internal.controllers;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceDto;
import com.sciencebot.pos.billing.dto.SendInvoiceEmailRequest;
import com.sciencebot.pos.billing.internal.controllers.doc.BillingInvoiceApiDoc;
import com.sciencebot.pos.billing.internal.services.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingInvoiceController implements BillingInvoiceApiDoc {

    private final InvoiceService invoiceService;

    public BillingInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Override
    @GetMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<Page<ElectronicInvoiceDto>> listInvoices(
            Pageable pageable,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(invoiceService.listInvoices(pageable, status));
    }

    @Override
    @GetMapping("/sales/{saleId}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<ElectronicInvoiceDto> getInvoiceBySaleId(@PathVariable Long saleId) {
        return invoiceService.getBySaleId(saleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    @PostMapping("/sales/{saleId}/retry")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR')")
    public ResponseEntity<ElectronicInvoiceDto> retryInvoice(@PathVariable Long saleId) {
        ElectronicInvoiceDto result = invoiceService.retryInvoice(saleId);
        return ResponseEntity.ok(result);
    }

    @Override
    @PostMapping("/sales/{saleId}/send-email")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<Void> sendInvoiceEmail(
            @PathVariable Long saleId,
            @Valid @RequestBody SendInvoiceEmailRequest request) {
        invoiceService.sendInvoiceEmail(saleId, request.email());
        return ResponseEntity.ok().build();
    }

    @Override
    @GetMapping("/sales/{saleId}/pdf")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long saleId) {
        byte[] pdf = invoiceService.downloadInvoicePDF(saleId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"factura-" + saleId + ".pdf\"")
                .body(pdf);
    }

    @Override
    @GetMapping("/sales/{saleId}/xml")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'SUPERVISOR', 'SELLER')")
    public ResponseEntity<byte[]> downloadXml(@PathVariable Long saleId) {
        byte[] xml = invoiceService.downloadInvoiceXML(saleId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"factura-" + saleId + ".xml\"")
                .body(xml);
    }
}
