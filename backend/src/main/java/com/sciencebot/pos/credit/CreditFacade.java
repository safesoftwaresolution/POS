package com.sciencebot.pos.credit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** API pública del fiado. Todo opera sobre el local actual. */
public interface CreditFacade {

    /** Cuenta del cliente (saldo en cero y sin cupo si nunca se le ha fiado). 404 si el cliente no existe. */
    CreditAccountDto getAccount(Long customerId);

    /**
     * Clientes con cuenta de fiado. Un vendedor solo recibe los clientes a quienes él les fió.
     *
     * @param onlyWithBalance true = solo los que hoy deben algo
     */
    Page<CreditAccountDto> listAccounts(String search, boolean onlyWithBalance, Pageable pageable);

    /** Movimientos de la cuenta, del más reciente al más antiguo. Un vendedor solo ve cuentas que él fió. */
    Page<CreditMovementDto> getMovements(Long customerId, Pageable pageable);

    /**
     * Suma una venta a fiado a la deuda del cliente. Falla (y la venta se revierte) si supera su cupo.
     *
     * @param dueDate fecha límite de pago; si es null se usan 30 días desde hoy
     */
    CreditMovementDto registerCharge(Long customerId, Long saleId, BigDecimal amount, LocalDate dueDate);

    /** Registra un abono. No puede superar la deuda ni hacerse con el método CREDIT. */
    CreditMovementDto registerPayment(Long customerId, RegisterPaymentCommand command);

    /**
     * Baja la deuda por la devolución de una venta a fiado, hasta el saldo que quede. Devuelve lo realmente
     * descontado de la deuda; si es menor que {@code amount}, la diferencia ya estaba pagada y se devuelve al cliente.
     */
    BigDecimal registerReturn(Long customerId, Long saleId, BigDecimal amount);

    /**
     * Fecha límite de pago con la que se fió una venta; vacío si la venta no se fió. Recibe el local porque la
     * factura electrónica se emite fuera de la solicitud HTTP, sin contexto de local.
     */
    Optional<LocalDate> findChargeDueDate(Long storeId, Long saleId);

    /** Fija o quita (null) el cupo máximo de deuda del cliente. */
    CreditAccountDto setCreditLimit(Long customerId, BigDecimal creditLimit);
}
