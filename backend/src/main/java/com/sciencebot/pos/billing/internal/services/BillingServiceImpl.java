package com.sciencebot.pos.billing.internal.services;

import com.sciencebot.pos.billing.BillingFacade;
import com.sciencebot.pos.billing.CreateNumberingRangeRequest;
import com.sciencebot.pos.billing.ElectronicInvoiceDto;
import com.sciencebot.pos.billing.NumberingRangeDto;
import com.sciencebot.pos.sales.SaleDto;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Fachada orquestadora del módulo de Facturación Electrónica.
 * Implementa {@link BillingFacade} delegando en los servicios especializados {@link InvoiceService} y {@link NumberingRangeService}.
 */
@Service
@Primary
public class BillingServiceImpl implements BillingFacade {

    private final InvoiceService invoiceService;
    private final NumberingRangeService numberingRangeService;

    public BillingServiceImpl(InvoiceService invoiceService, NumberingRangeService numberingRangeService) {
        this.invoiceService = invoiceService;
        this.numberingRangeService = numberingRangeService;
    }

    // --- Facturación de Ventas ---

    @Override
    public ElectronicInvoiceDto processElectronicInvoice(SaleDto sale) {
        return invoiceService.processElectronicInvoice(sale);
    }

    @Override
    public Optional<ElectronicInvoiceDto> getBySaleId(Long saleId) {
        return invoiceService.getBySaleId(saleId);
    }

    @Override
    public ElectronicInvoiceDto retryInvoice(Long saleId) {
        return invoiceService.retryInvoice(saleId);
    }

    @Override
    public void sendInvoiceEmail(Long saleId, String email) {
        invoiceService.sendInvoiceEmail(saleId, email);
    }

    // --- Rangos de Numeración ---

    @Override
    public List<NumberingRangeDto> queryDianNumberingRanges() {
        return numberingRangeService.queryDianNumberingRanges();
    }

    @Override
    public List<NumberingRangeDto> listNumberingRanges() {
        return numberingRangeService.listNumberingRanges();
    }

    @Override
    public NumberingRangeDto getNumberingRange(Long numberingRangeId) {
        return numberingRangeService.getNumberingRange(numberingRangeId);
    }

    @Override
    public NumberingRangeDto createNumberingRange(CreateNumberingRangeRequest request) {
        return numberingRangeService.createNumberingRange(request);
    }

    @Override
    public void deleteNumberingRange(Long numberingRangeId) {
        numberingRangeService.deleteNumberingRange(numberingRangeId);
    }

    @Override
    public NumberingRangeDto toggleNumberingRangeStatus(Long numberingRangeId) {
        return numberingRangeService.toggleNumberingRangeStatus(numberingRangeId);
    }

    @Override
    public NumberingRangeDto updateNumberingRangeCurrent(Long numberingRangeId, Long current) {
        return numberingRangeService.updateNumberingRangeCurrent(numberingRangeId, current);
    }
}
