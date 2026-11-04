package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.sales.internal.entities.Sale;
import com.sciencebot.pos.sales.internal.repositories.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ejecuta con H2 real las consultas nativas que limitan las facturas a las ventas de un vendedor. */
@SpringBootTest
@Transactional
class ElectronicInvoiceSellerQueryTest {

    private static final long STORE = 9101L;
    private static final long OTHER_STORE = 9102L;

    @Autowired
    private ElectronicInvoiceRepository invoices;

    @Autowired
    private SaleRepository sales;

    private long invoiceFor(long storeId, long userId, String number) {
        Sale sale = new Sale();
        sale.setStoreId(storeId);
        sale.setUserId(userId);
        sale.setCustomerId(1L);
        sale.setInvoiceNumber(number);
        sale.setTotalAmount(BigDecimal.TEN);
        sale.setCashReceived(BigDecimal.TEN);
        sale.setCashChange(BigDecimal.ZERO);
        sale.setPaymentMethod("CASH");
        sale.setCreatedAt(LocalDateTime.now());
        Sale saved = sales.saveAndFlush(sale);
        invoices.saveAndFlush(ElectronicInvoice.initialize(saved.getId(), storeId));
        return saved.getId();
    }

    @Test
    void onlyInvoicesOfTheSellersOwnSalesAreListed() {
        long mine = invoiceFor(STORE, 7L, "T-9101-1");
        invoiceFor(STORE, 8L, "T-9101-2");           // otro vendedor, mismo local
        invoiceFor(OTHER_STORE, 7L, "T-9102-1");     // mismo usuario, otro local

        var page = invoices.findByStoreIdAndSeller(STORE, 7L, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals(mine, page.getContent().get(0).getSaleId());
    }

    @Test
    void statusFilterAlsoRespectsTheSeller() {
        invoiceFor(STORE, 7L, "T-9101-3");
        invoiceFor(STORE, 8L, "T-9101-4");

        var pending = invoices.findByStoreIdAndStatusAndSeller(
                STORE, ElectronicInvoiceStatus.PENDING.name(), 7L, PageRequest.of(0, 20));
        var validated = invoices.findByStoreIdAndStatusAndSeller(
                STORE, ElectronicInvoiceStatus.VALIDATED.name(), 7L, PageRequest.of(0, 20));

        assertEquals(1, pending.getTotalElements());
        assertEquals(0, validated.getTotalElements());
    }
}
