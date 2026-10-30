package com.sciencebot.pos.credit.internal.services;

import com.sciencebot.pos.audit.AuditAction;
import com.sciencebot.pos.audit.AuditFacade;
import com.sciencebot.pos.audit.FieldChange;
import com.sciencebot.pos.config.tenant.TenantContext;
import com.sciencebot.pos.credit.CreditAccountDto;
import com.sciencebot.pos.credit.CreditFacade;
import com.sciencebot.pos.credit.CreditMovementDto;
import com.sciencebot.pos.credit.CreditMovementType;
import com.sciencebot.pos.credit.RegisterPaymentCommand;
import com.sciencebot.pos.credit.internal.entities.CreditAccount;
import com.sciencebot.pos.credit.internal.entities.CreditMovement;
import com.sciencebot.pos.credit.internal.mappers.CreditMovementMapper;
import com.sciencebot.pos.credit.internal.repositories.CreditAccountReadDao;
import com.sciencebot.pos.credit.internal.repositories.CreditAccountRepository;
import com.sciencebot.pos.credit.internal.repositories.CreditMovementRepository;
import com.sciencebot.pos.customers.CustomerDto;
import com.sciencebot.pos.customers.CustomerFacade;
import com.sciencebot.pos.users.UserFacade;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CreditServiceImpl implements CreditFacade {

    /** Método de pago de las ventas a fiado (valor del tema PAYMENT_METHODS). */
    public static final String CREDIT_METHOD = "CREDIT";
    private static final int DEFAULT_DUE_DAYS = 30;
    private static final String AUDIT_ENTITY = "CREDIT_ACCOUNT";

    private final CreditAccountRepository accountRepository;
    private final CreditMovementRepository movementRepository;
    private final CreditAccountReadDao readDao;
    private final CreditMovementMapper movementMapper;
    private final CustomerFacade customerFacade;
    private final UserFacade userFacade;
    private final AuditFacade auditFacade;

    public CreditServiceImpl(CreditAccountRepository accountRepository, CreditMovementRepository movementRepository,
                             CreditAccountReadDao readDao, CreditMovementMapper movementMapper,
                             CustomerFacade customerFacade, UserFacade userFacade, AuditFacade auditFacade) {
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.readDao = readDao;
        this.movementMapper = movementMapper;
        this.customerFacade = customerFacade;
        this.userFacade = userFacade;
        this.auditFacade = auditFacade;
    }

    /** Quién hace la solicitud: para firmar los movimientos y para limitar al vendedor a sus cuentas. */
    private record Actor(Long userId, String username, boolean seller) {
    }

    private Actor currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return new Actor(null, null, false);
        }
        boolean seller = auth.getAuthorities().stream().anyMatch(a -> "ROLE_SELLER".equals(a.getAuthority()));
        Long userId = userFacade.findByUsername(auth.getName()).map(u -> u.id()).orElse(null);
        return new Actor(userId, auth.getName(), seller);
    }

    @Override
    public CreditAccountDto getAccount(Long customerId) {
        Long storeId = requireStoreId();
        CustomerDto customer = requireCustomer(customerId);
        Optional<CreditAccount> account = accountRepository.findByStoreIdAndCustomerId(storeId, customerId);
        if (account.isEmpty()) {
            return toDto(customer, BigDecimal.ZERO, null, null);
        }
        CreditAccount a = account.get();
        BigDecimal balance = accountRepository.findBalance(a.getId(), storeId);
        var last = movementRepository.findFirstByStoreIdAndAccountIdOrderByCreatedAtDescIdDesc(storeId, a.getId());
        return toDto(customer, balance, a.getCreditLimit(), last.map(CreditMovement::getCreatedAt).orElse(null));
    }

    @Override
    public Page<CreditAccountDto> listAccounts(String search, boolean onlyWithBalance, Pageable pageable) {
        Actor actor = currentActor();
        String cleanSearch = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();
        if (actor.seller() && actor.userId() == null) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return readDao.findAccounts(requireStoreId(), cleanSearch, onlyWithBalance,
                actor.seller() ? actor.userId() : null, pageable);
    }

    @Override
    public Page<CreditMovementDto> getMovements(Long customerId, Pageable pageable) {
        Long storeId = requireStoreId();
        requireCustomer(customerId);
        CreditAccount account = accountRepository.findByStoreIdAndCustomerId(storeId, customerId)
                .orElseThrow(() -> new EntityNotFoundException("El cliente no tiene cuenta de fiado"));
        Actor actor = currentActor();
        if (actor.seller() && !movementRepository.existsByStoreIdAndAccountIdAndTypeAndUserId(
                storeId, account.getId(), CreditMovementType.CHARGE, actor.userId())) {
            // Un vendedor solo ve las cuentas a las que él les fió: las demás responden como inexistentes.
            throw new EntityNotFoundException("El cliente no tiene cuenta de fiado");
        }
        return movementRepository.findByStoreIdAndAccountIdOrderByCreatedAtDescIdDesc(storeId, account.getId(), pageable)
                .map(movementMapper::toDto);
    }

    @Override
    @Transactional
    public CreditMovementDto registerCharge(Long customerId, Long saleId, BigDecimal amount, LocalDate dueDate) {
        BigDecimal value = requirePositive(amount, "El monto a fiar debe ser mayor a cero");
        Long storeId = requireStoreId();
        CustomerDto customer = requireCustomer(customerId);
        CreditAccount account = findOrCreateAccount(storeId, customerId);

        int updated = accountRepository.addDebt(account.getId(), storeId, value, LocalDateTime.now());
        if (updated == 0) {
            BigDecimal balance = accountRepository.findBalance(account.getId(), storeId);
            throw new IllegalArgumentException("El fiado supera el cupo de " + customer.fullName() + " (cupo: "
                    + account.getCreditLimit() + ", deuda actual: " + balance + ", se intentó fiar: " + value + ")");
        }
        LocalDate due = dueDate != null ? dueDate : LocalDate.now().plusDays(DEFAULT_DUE_DAYS);
        return record(storeId, account, customerId, CreditMovementType.CHARGE, value, saleId, null, due, null);
    }

    @Override
    @Transactional
    public CreditMovementDto registerPayment(Long customerId, RegisterPaymentCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Los datos del abono son obligatorios");
        }
        BigDecimal value = requirePositive(command.amount(), "El abono debe ser mayor a cero");
        String method = (command.paymentMethod() == null || command.paymentMethod().isBlank())
                ? "CASH" : command.paymentMethod().trim().toUpperCase();
        if (CREDIT_METHOD.equals(method)) {
            throw new IllegalArgumentException("Un abono no se puede pagar a fiado");
        }
        Long storeId = requireStoreId();
        requireCustomer(customerId);
        CreditAccount account = accountRepository.findByStoreIdAndCustomerId(storeId, customerId)
                .filter(a -> accountRepository.findBalance(a.getId(), storeId).signum() > 0)
                .orElseThrow(() -> new IllegalArgumentException("El cliente no tiene deuda pendiente"));

        if (accountRepository.reduceDebt(account.getId(), storeId, value, LocalDateTime.now()) == 0) {
            BigDecimal balance = accountRepository.findBalance(account.getId(), storeId);
            throw new IllegalArgumentException("El abono (" + value + ") supera la deuda actual del cliente (" + balance + ")");
        }
        String note = (command.note() == null || command.note().isBlank()) ? null : command.note().trim();
        return record(storeId, account, customerId, CreditMovementType.PAYMENT, value, null, method, null, note);
    }

    @Override
    @Transactional
    public BigDecimal registerReturn(Long customerId, Long saleId, BigDecimal amount) {
        BigDecimal value = requirePositive(amount, "El monto de la devolución debe ser mayor a cero");
        Long storeId = requireStoreId();
        Optional<CreditAccount> found = accountRepository.findByStoreIdAndCustomerId(storeId, customerId);
        if (found.isEmpty()) {
            return BigDecimal.ZERO;
        }
        CreditAccount account = found.get();
        // Se descuenta hasta lo que aún se debe: si el cliente ya había pagado esa parte, se le devuelve en dinero.
        for (int attempt = 0; attempt < 3; attempt++) {
            BigDecimal applied = value.min(accountRepository.findBalance(account.getId(), storeId));
            if (applied.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            if (accountRepository.reduceDebt(account.getId(), storeId, applied, LocalDateTime.now()) > 0) {
                record(storeId, account, customerId, CreditMovementType.RETURN, applied, saleId, null, null, null);
                return applied;
            }
        }
        throw new IllegalStateException("No se pudo aplicar la devolución a la cuenta de fiado; intenta de nuevo");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDate> findChargeDueDate(Long storeId, Long saleId) {
        if (storeId == null || saleId == null) {
            return Optional.empty();
        }
        return movementRepository.findFirstByStoreIdAndSaleIdAndType(storeId, saleId, CreditMovementType.CHARGE)
                .map(CreditMovement::getDueDate);
    }

    @Override
    @Transactional
    public CreditAccountDto setCreditLimit(Long customerId, BigDecimal creditLimit) {
        if (creditLimit != null && creditLimit.signum() < 0) {
            throw new IllegalArgumentException("El cupo no puede ser negativo");
        }
        Long storeId = requireStoreId();
        requireCustomer(customerId);
        CreditAccount account = findOrCreateAccount(storeId, customerId);
        BigDecimal newLimit = creditLimit == null ? null : creditLimit.setScale(2, RoundingMode.HALF_UP);
        BigDecimal oldLimit = account.getCreditLimit();
        if (oldLimit == null ? newLimit != null : (newLimit == null || oldLimit.compareTo(newLimit) != 0)) {
            account.setCreditLimit(newLimit);
            accountRepository.save(account);
            Actor actor = currentActor();
            auditFacade.record(AUDIT_ENTITY, customerId, AuditAction.UPDATE, actor.userId(), actor.username(),
                    Map.of("creditLimit", new FieldChange(oldLimit, newLimit)));
        }
        return getAccount(customerId);
    }

    private CreditMovementDto record(Long storeId, CreditAccount account, Long customerId, CreditMovementType type,
                                     BigDecimal amount, Long saleId, String paymentMethod, LocalDate dueDate, String note) {
        Actor actor = currentActor();
        CreditMovement movement = new CreditMovement();
        movement.setStoreId(storeId);
        movement.setAccountId(account.getId());
        movement.setCustomerId(customerId);
        movement.setType(type);
        movement.setAmount(amount);
        movement.setBalanceAfter(accountRepository.findBalance(account.getId(), storeId));
        movement.setSaleId(saleId);
        movement.setPaymentMethod(paymentMethod);
        movement.setDueDate(dueDate);
        movement.setNote(note);
        movement.setUserId(actor.userId());
        movement.setUsername(actor.username());
        return movementMapper.toDto(movementRepository.save(movement));
    }

    private CreditAccount findOrCreateAccount(Long storeId, Long customerId) {
        return accountRepository.findByStoreIdAndCustomerId(storeId, customerId).orElseGet(() -> {
            CreditAccount created = new CreditAccount();
            created.setStoreId(storeId);
            created.setCustomerId(customerId);
            return accountRepository.saveAndFlush(created);
        });
    }

    private CustomerDto requireCustomer(Long customerId) {
        return customerFacade.getById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + customerId));
    }

    private static CreditAccountDto toDto(CustomerDto customer, BigDecimal balance, BigDecimal limit, LocalDateTime last) {
        BigDecimal available = limit == null ? null : limit.subtract(balance).max(BigDecimal.ZERO);
        return new CreditAccountDto(customer.id(), customer.fullName(), customer.identification(), balance, limit, available, last);
    }

    private static BigDecimal requirePositive(BigDecimal amount, String message) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static Long requireStoreId() {
        Long storeId = TenantContext.getStoreId();
        if (storeId == null) {
            throw new IllegalStateException("No hay un local activo en el contexto de la solicitud");
        }
        return storeId;
    }
}
