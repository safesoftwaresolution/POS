package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.ElectronicInvoiceDto;
import com.sciencebot.pos.sales.SaleDto;

import java.util.Optional;

/**
 * Servicio interno para la emisión, consulta, reintento y envío por email de Facturas Electrónicas.
 */
public interface InvoiceService {

    ElectronicInvoiceDto processElectronicInvoice(SaleDto sale);

    Optional<ElectronicInvoiceDto> getBySaleId(Long saleId);

    ElectronicInvoiceDto retryInvoice(Long saleId);

    void sendInvoiceEmail(Long saleId, String email);

    org.springframework.data.domain.Page<ElectronicInvoiceDto> listInvoices(org.springframework.data.domain.Pageable pageable, String status);

    byte[] downloadInvoicePDF(Long saleId);

    byte[] downloadInvoiceXML(Long saleId);
}
