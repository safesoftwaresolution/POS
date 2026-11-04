package com.sciencebot.pos.billing;

import com.sciencebot.pos.billing.dto.ElectronicInvoiceStatus;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice;
import com.sciencebot.pos.billing.internal.entities.ElectronicInvoice.ReserveOutcome;
import com.sciencebot.pos.billing.internal.services.EmissionRetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Reserva (lease) de una emisión y reintento con espera creciente: reglas del dominio de la factura. */
class ElectronicInvoiceEmissionTest {

    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    private static ElectronicInvoice newInvoice() {
        return ElectronicInvoice.initialize(1L, 1L);
    }

    @Test
    void reserve_OnAFreshInvoiceTakesTheLeaseAndCountsTheAttempt() {
        ElectronicInvoice invoice = newInvoice();

        assertEquals(ReserveOutcome.RESERVED, invoice.reserveForEmission(NOW, LEASE));

        assertEquals(1, invoice.getAttempts());
        assertEquals(NOW, invoice.getProcessingStartedAt());
        assertEquals(ElectronicInvoiceStatus.PENDING, invoice.getStatus());
    }

    @Test
    void reserve_WhileTheLeaseIsAliveReportsInProgressWithoutCountingAnotherAttempt() {
        ElectronicInvoice invoice = newInvoice();
        invoice.reserveForEmission(NOW, LEASE);

        assertEquals(ReserveOutcome.IN_PROGRESS, invoice.reserveForEmission(NOW.plusMinutes(4), LEASE));

        assertEquals(1, invoice.getAttempts());
        assertEquals(NOW, invoice.getProcessingStartedAt());
    }

    @Test
    void reserve_AfterTheLeaseExpiredAnotherWorkerTakesOver() {
        ElectronicInvoice invoice = newInvoice();
        invoice.reserveForEmission(NOW, LEASE);

        assertEquals(ReserveOutcome.RESERVED, invoice.reserveForEmission(NOW.plusMinutes(6), LEASE));

        assertEquals(2, invoice.getAttempts());
        assertEquals(NOW.plusMinutes(6), invoice.getProcessingStartedAt());
    }

    @Test
    void reserve_OnAValidatedInvoiceChangesNothing() {
        ElectronicInvoice invoice = newInvoice();
        invoice.markAsValidated("SETP-1", "cufe", "qr", "pdf", NOW);

        assertEquals(ReserveOutcome.ALREADY_VALIDATED, invoice.reserveForEmission(NOW, LEASE));

        assertEquals(0, invoice.getAttempts());
        assertNull(invoice.getProcessingStartedAt());
        assertEquals(ElectronicInvoiceStatus.VALIDATED, invoice.getStatus());
    }

    @Test
    void reserve_AfterAnErrorStartsANewAttempt() {
        ElectronicInvoice invoice = newInvoice();
        invoice.reserveForEmission(NOW, LEASE);
        invoice.markAsError("timeout");
        invoice.completeAttempt(NOW.plusMinutes(2));

        assertEquals(ReserveOutcome.RESERVED, invoice.reserveForEmission(NOW.plusMinutes(3), LEASE));

        assertEquals(2, invoice.getAttempts());
        assertEquals(ElectronicInvoiceStatus.PENDING, invoice.getStatus());
        assertNull(invoice.getErrorMessage());
        assertNull(invoice.getNextRetryAt());
    }

    @Test
    void completeAttempt_ReleasesTheLeaseAndSchedulesTheRetry() {
        ElectronicInvoice invoice = newInvoice();
        invoice.reserveForEmission(NOW, LEASE);

        invoice.completeAttempt(NOW.plusMinutes(2));

        assertNull(invoice.getProcessingStartedAt());
        assertEquals(NOW.plusMinutes(2), invoice.getNextRetryAt());
    }

    @Test
    void markAsPending_KeepsTheInvoicePendingWithTheProviderMessage() {
        ElectronicInvoice invoice = newInvoice();
        invoice.reserveForEmission(NOW, LEASE);

        invoice.markAsPending("La DIAN aún no responde");

        assertEquals(ElectronicInvoiceStatus.PENDING, invoice.getStatus());
        assertEquals("La DIAN aún no responde", invoice.getErrorMessage());
    }

    @Test
    void manualRetry_ResetsTheAttemptsAndTheSchedule() {
        ElectronicInvoice invoice = newInvoice();
        invoice.reserveForEmission(NOW, LEASE);
        invoice.markAsError("fallo");
        invoice.completeAttempt(NOW.plusHours(1));

        invoice.prepareForRetry();

        assertEquals(0, invoice.getAttempts());
        assertNull(invoice.getNextRetryAt());
        assertNull(invoice.getProcessingStartedAt());
    }

    @Test
    void retryPolicy_GrowsByDoublingAndCapsAtOneHour() {
        assertEquals(Duration.ofMinutes(2), EmissionRetryPolicy.baseDelay(1));
        assertEquals(Duration.ofMinutes(4), EmissionRetryPolicy.baseDelay(2));
        assertEquals(Duration.ofMinutes(8), EmissionRetryPolicy.baseDelay(3));
        assertEquals(Duration.ofMinutes(32), EmissionRetryPolicy.baseDelay(5));
        assertEquals(Duration.ofMinutes(60), EmissionRetryPolicy.baseDelay(6));
        assertEquals(Duration.ofMinutes(60), EmissionRetryPolicy.baseDelay(40));
    }

    @Test
    void retryPolicy_AddsAtMostTenPercentOfJitter() {
        for (int i = 0; i < 200; i++) {
            LocalDateTime next = EmissionRetryPolicy.nextRetryAt(3, NOW);
            assertFalse(next.isBefore(NOW.plusMinutes(8)));
            assertFalse(next.isAfter(NOW.plusMinutes(8).plusSeconds(49)), next.toString());
        }
    }
}
