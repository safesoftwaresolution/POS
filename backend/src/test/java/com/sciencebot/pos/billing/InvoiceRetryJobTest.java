package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.BillingActivationStatus;
import com.sciencebot.pos.billing.dto.BillingEnvironment;
import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.adapters.dto.InvoiceRequest;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.entities.StoreBillingConfig;
import com.sciencebot.pos.billing.internal.listeners.InvoiceRetryJob;
import com.sciencebot.pos.billing.internal.mappers.BillingCanonicalMapper;
import com.sciencebot.pos.billing.internal.repositories.ElectronicInvoiceRepository;
import com.sciencebot.pos.billing.internal.repositories.StoreBillingConfigRepository;
import com.sciencebot.pos.billing.internal.services.InvoiceService;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.sales.SaleDto;
import com.sciencebot.pos.sales.SaleFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reintento automatico: solo lo que corresponde, con el local en contexto y sin que un fallo detenga el lote. */
@SpringBootTest(properties = "billing.provider=mock")
class InvoiceRetryJobTest {

    private static final Duration LEASE = Duration.ofMinutes(5);

    @Autowired
    private ElectronicInvoiceRepository repository;
    @Autowired
    private StoreBillingConfigRepository configRepository;
    @Autowired
    private InvoiceService invoiceService;
    @MockitoBean
    private BillingCanonicalMapper canonicalMapper;
    @MockitoBean
    private SaleFacade saleFacade;

    private InvoiceRetryJob job;
    private LocalDateTime now;
    private Long storeId;
    private final List<Long> createdSales = new ArrayList<>();

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        storeId = 600_000L + (System.nanoTime() % 1_000_000L);
        saveStoreConfig(storeId, BillingActivationStatus.ACTIVE);
        when(canonicalMapper.toInvoiceRequest(any())).thenReturn(mock(InvoiceRequest.class));
        when(saleFacade.getById(any())).thenAnswer(inv -> Optional.of(sale(inv.getArgument(0))));
        job = new InvoiceRetryJob(repository, configRepository, saleFacade, invoiceService, 20, 8, LEASE);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        createdSales.forEach(id -> repository.findBySaleId(id).ifPresent(repository::delete));
        configRepository.findAll().stream()
                .filter(c -> c.getStoreId() >= 600_000L && c.getStoreId() < 1_600_000L)
                .forEach(configRepository::delete);
    }

    private void saveStoreConfig(Long id, BillingActivationStatus status) {
        StoreBillingConfig config = new StoreBillingConfig();
        config.setStoreId(id);
        config.setEnvironment(BillingEnvironment.SANDBOX);
        config.setStatus(status);
        configRepository.save(config);
    }

    private SaleDto sale(Long saleId) {
        return new SaleDto(saleId, storeId, null, "FACT-" + saleId, null, "Cliente", BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.ZERO, "admin", List.of(), "CASH");
    }

    private Long invoice(Consumer<ElectronicInvoice> state) {
        Long saleId = 700_000L + (System.nanoTime() % 1_000_000L);
        ElectronicInvoice invoice = ElectronicInvoice.initialize(saleId, storeId);
        state.accept(invoice);
        repository.save(invoice);
        createdSales.add(saleId);
        return saleId;
    }

    private void failedAttempt(ElectronicInvoice invoice, LocalDateTime at, LocalDateTime retryAt) {
        invoice.reserveForEmission(at, LEASE);
        invoice.markAsError("timeout");
        invoice.completeAttempt(retryAt);
    }

    private ElectronicInvoice stored(Long saleId) {
        return repository.findBySaleId(saleId).orElseThrow();
    }

    @Test
    void anErrorWhoseRetryIsDueIsEmittedAgainAndValidated() {
        Long saleId = invoice(i -> failedAttempt(i, now.minusMinutes(30), now.minusMinutes(1)));

        assertEquals(1, job.retryDue(now));

        ElectronicInvoice after = stored(saleId);
        assertEquals(ElectronicInvoiceStatus.VALIDATED, after.getStatus());
        assertEquals(2, after.getAttempts());
    }

    @Test
    void whatIsNotDueIsLeftAlone() {
        Long notYet = invoice(i -> failedAttempt(i, now.minusMinutes(30), now.plusHours(1)));
        Long exhausted = invoice(i -> {
            for (int n = 0; n < 8; n++) {
                failedAttempt(i, now.minusHours(10).plusMinutes(10L * n), now.minusMinutes(1));
            }
        });
        Long rejected = invoice(i -> {
            i.reserveForEmission(now.minusMinutes(30), LEASE);
            i.markAsRejected("NIT inválido");
            i.completeAttempt(null);
        });
        Long validated = invoice(i -> i.markAsValidated("SETP-1", "cufe", "qr", "pdf", now));
        Long liveLease = invoice(i -> i.reserveForEmission(now.minusMinutes(1), LEASE));

        assertEquals(0, job.retryDue(now));

        assertEquals(1, stored(notYet).getAttempts());
        assertEquals(8, stored(exhausted).getAttempts());
        assertEquals(ElectronicInvoiceStatus.ERROR, stored(exhausted).getStatus(), "agotada: queda visible como ERROR");
        assertEquals(ElectronicInvoiceStatus.REJECTED, stored(rejected).getStatus());
        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored(validated).getStatus());
        assertEquals(1, stored(liveLease).getAttempts());
        assertEquals(ElectronicInvoiceStatus.PENDING, stored(liveLease).getStatus());
    }

    @Test
    void aPendingInvoiceWhoseLeaseExpiredIsTakenOver() {
        Long saleId = invoice(i -> i.reserveForEmission(now.minusMinutes(10), LEASE));

        assertEquals(1, job.retryDue(now));

        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored(saleId).getStatus());
        assertEquals(2, stored(saleId).getAttempts());
    }

    @Test
    void storesWithoutActiveBillingAreSkipped() {
        Long inactiveStore = storeId + 1;
        saveStoreConfig(inactiveStore, BillingActivationStatus.SUSPENDED);
        ElectronicInvoice other = ElectronicInvoice.initialize(800_000L + (System.nanoTime() % 1_000_000L), inactiveStore);
        failedAttempt(other, now.minusMinutes(30), now.minusMinutes(1));
        repository.save(other);
        createdSales.add(other.getSaleId());

        assertEquals(0, job.retryDue(now));

        assertEquals(ElectronicInvoiceStatus.ERROR, stored(other.getSaleId()).getStatus());
        assertEquals(1, stored(other.getSaleId()).getAttempts());
    }

    @Test
    void invoicesOfStoresWithoutActiveBillingNeverFillTheBatch() {
        Long inactiveStore = storeId + 1;
        saveStoreConfig(inactiveStore, BillingActivationStatus.SUSPENDED);
        for (int n = 0; n < 2; n++) {
            ElectronicInvoice blocked = ElectronicInvoice.initialize(800_000L + (System.nanoTime() % 1_000_000L), inactiveStore);
            failedAttempt(blocked, now.minusMinutes(30), now.minusMinutes(1));
            repository.save(blocked);
            createdSales.add(blocked.getSaleId());
        }
        Long active = invoice(i -> failedAttempt(i, now.minusMinutes(30), now.minusMinutes(1)));
        InvoiceRetryJob small = new InvoiceRetryJob(repository, configRepository, saleFacade, invoiceService, 2, 8, LEASE);

        assertEquals(1, small.retryDue(now));

        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored(active).getStatus());
    }

    @Test
    void oneFailingInvoiceDoesNotStopTheBatchAndTheTenantIsRestored() {
        Long broken = invoice(i -> failedAttempt(i, now.minusMinutes(30), now.minusMinutes(1)));
        Long healthy = invoice(i -> failedAttempt(i, now.minusMinutes(30), now.minusMinutes(1)));
        when(saleFacade.getById(broken)).thenThrow(new IllegalStateException("ventas caído"));
        TenantContext.setStoreId(42L);

        assertEquals(1, job.retryDue(now));

        assertEquals(ElectronicInvoiceStatus.ERROR, stored(broken).getStatus());
        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored(healthy).getStatus());
        assertEquals(42L, TenantContext.getStoreId(), "el job no puede dejar otro local en el contexto");
    }

    @Test
    void theBatchSizeLimitsHowManyAreTakenPerRun() {
        Long first = invoice(i -> failedAttempt(i, now.minusMinutes(30), now.minusMinutes(1)));
        Long second = invoice(i -> failedAttempt(i, now.minusMinutes(30), now.minusMinutes(1)));
        InvoiceRetryJob small = new InvoiceRetryJob(repository, configRepository, saleFacade, invoiceService, 1, 8, LEASE);

        assertEquals(1, small.retryDue(now));
        assertEquals(1, small.retryDue(now));

        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored(first).getStatus());
        assertEquals(ElectronicInvoiceStatus.VALIDATED, stored(second).getStatus());
    }
}
