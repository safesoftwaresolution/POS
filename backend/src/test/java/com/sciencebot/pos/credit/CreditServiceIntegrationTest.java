package com.sciencebot.pos.credit;

import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.customers.CreateCustomerCommand;
import com.sciencebot.pos.customers.CustomerFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Con Hibernate y H2 reales: el saldo se mueve de forma atómica, respeta el cupo y la lista une al cliente. */
@SpringBootTest
@Transactional
class CreditServiceIntegrationTest {

    private static final long STORE = 9301L;

    @Autowired
    private CreditFacade creditFacade;

    @Autowired
    private CustomerFacade customerFacade;

    private Long maria;
    private Long pedro;

    @BeforeEach
    void setUp() {
        TenantContext.setStoreId(STORE);
        maria = customerFacade.createCustomer(new CreateCustomerCommand("Maria Lopez", "1001", null, null, null)).id();
        pedro = customerFacade.createCustomer(new CreateCustomerCommand("Pedro Ruiz", "1002", null, null, null)).id();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aCustomerWithoutAccountOwesNothing() {
        CreditAccountDto account = creditFacade.getAccount(maria);

        assertEquals(0, account.balance().signum());
        assertNull(account.creditLimit());
        assertNull(account.availableCredit());
    }

    @Test
    void chargesAddUpAndPaymentsReduceTheDebt() {
        creditFacade.registerCharge(maria, 100L, money("30000"), null);
        CreditMovementDto second = creditFacade.registerCharge(maria, 101L, money("20000"), LocalDate.of(2026, 12, 1));
        assertEquals(0, second.balanceAfter().compareTo(money("50000")));
        assertEquals(LocalDate.of(2026, 12, 1), second.dueDate());

        CreditMovementDto payment = creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("15000"), "transfer", "pagó"));

        assertEquals(CreditMovementType.PAYMENT, payment.type());
        assertEquals("TRANSFER", payment.paymentMethod());
        assertEquals(0, payment.balanceAfter().compareTo(money("35000")));
        assertEquals(0, creditFacade.getAccount(maria).balance().compareTo(money("35000")));

        var movements = creditFacade.getMovements(maria, PageRequest.of(0, 10)).getContent();
        assertEquals(3, movements.size());
        assertEquals(CreditMovementType.PAYMENT, movements.get(0).type(), "el más reciente primero");
    }

    @Test
    void aChargeWithoutADueDateDefaultsToThirtyDays() {
        CreditMovementDto charge = creditFacade.registerCharge(maria, 100L, money("1000"), null);

        assertEquals(LocalDate.now().plusDays(30), charge.dueDate());
    }

    @Test
    void aChargeAboveTheLimitIsRefusedAndTheBalanceDoesNotMove() {
        creditFacade.setCreditLimit(maria, money("50000"));
        creditFacade.registerCharge(maria, 100L, money("40000"), null);

        assertThrows(IllegalArgumentException.class, () -> creditFacade.registerCharge(maria, 101L, money("10001"), null));

        CreditAccountDto account = creditFacade.getAccount(maria);
        assertEquals(0, account.balance().compareTo(money("40000")));
        assertEquals(0, account.availableCredit().compareTo(money("10000")));
        // justo hasta el cupo sí se puede
        creditFacade.registerCharge(maria, 102L, money("10000"), null);
        assertEquals(0, creditFacade.getAccount(maria).availableCredit().signum());
    }

    @Test
    void removingTheLimitAllowsAnyDebt() {
        creditFacade.setCreditLimit(maria, money("1000"));
        creditFacade.setCreditLimit(maria, null);

        creditFacade.registerCharge(maria, 100L, money("999999"), null);

        assertNull(creditFacade.getAccount(maria).creditLimit());
    }

    @Test
    void aPaymentCannotExceedTheDebt() {
        creditFacade.registerCharge(maria, 100L, money("10000"), null);

        assertThrows(IllegalArgumentException.class,
                () -> creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("10000.01"), "CASH", null)));

        assertEquals(0, creditFacade.getAccount(maria).balance().compareTo(money("10000")));
        creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("10000"), "CASH", null));
        assertEquals(0, creditFacade.getAccount(maria).balance().signum());
    }

    @Test
    void invalidPaymentsAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("500"), "CASH", null)),
                "sin deuda no hay nada que abonar");

        creditFacade.registerCharge(maria, 100L, money("10000"), null);
        assertThrows(IllegalArgumentException.class,
                () -> creditFacade.registerPayment(maria, new RegisterPaymentCommand(BigDecimal.ZERO, "CASH", null)));
        assertThrows(IllegalArgumentException.class,
                () -> creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("-5"), "CASH", null)));
        assertThrows(IllegalArgumentException.class,
                () -> creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("500"), "CREDIT", null)),
                "un abono no se paga a fiado");
    }

    @Test
    void aReturnLowersTheDebtOnlyUpToWhatIsStillOwed() {
        creditFacade.registerCharge(maria, 100L, money("30000"), null);
        creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("25000"), "CASH", null));

        BigDecimal applied = creditFacade.registerReturn(maria, 100L, money("12000"));

        assertEquals(0, applied.compareTo(money("5000")), "solo quedaban 5000 por pagar; el resto se devuelve en dinero");
        assertEquals(0, creditFacade.getAccount(maria).balance().signum());
    }

    @Test
    void aReturnForACustomerWithoutAccountAppliesNothing() {
        assertEquals(0, creditFacade.registerReturn(maria, 100L, money("1000")).signum());
    }

    @Test
    void aCustomerWithDebtCannotBeDeleted() {
        creditFacade.registerCharge(maria, 100L, money("1000"), null);

        assertThrows(IllegalStateException.class, () -> customerFacade.deleteCustomer(maria));

        creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("1000"), "CASH", null));
        customerFacade.deleteCustomer(maria);
    }

    @Test
    void theListShowsDebtorsBiggestFirstAndHonoursSearchAndTheDebtFilter() {
        creditFacade.registerCharge(maria, 100L, money("10000"), null);
        creditFacade.registerCharge(pedro, 101L, money("90000"), null);
        creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("10000"), "CASH", null));
        creditFacade.registerCharge(maria, 102L, money("500"), null);

        var debtors = creditFacade.listAccounts(null, true, PageRequest.of(0, 10));
        assertEquals(2, debtors.getTotalElements());
        assertEquals("Pedro Ruiz", debtors.getContent().get(0).customerName(), "la mayor deuda primero");
        assertNotNull(debtors.getContent().get(0).lastMovementAt());

        assertEquals(1, creditFacade.listAccounts("maria", true, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, creditFacade.listAccounts("1002", true, PageRequest.of(0, 10)).getTotalElements(), "también por identificación");

        creditFacade.registerPayment(maria, new RegisterPaymentCommand(money("500"), "CASH", null));
        assertEquals(1, creditFacade.listAccounts(null, true, PageRequest.of(0, 10)).getTotalElements(), "Maria ya no debe");
        assertEquals(2, creditFacade.listAccounts(null, false, PageRequest.of(0, 10)).getTotalElements(), "con saldo cero si se pide");
    }

    @Test
    void accountsOfAnotherStoreAreInvisible() {
        creditFacade.registerCharge(maria, 100L, money("1000"), null);

        TenantContext.setStoreId(9302L);
        assertEquals(0, creditFacade.listAccounts(null, true, PageRequest.of(0, 10)).getTotalElements());
        assertThrows(jakarta.persistence.EntityNotFoundException.class, () -> creditFacade.getAccount(maria));
    }
}
