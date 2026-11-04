package com.sciencebot.pos.credit.internal.repositories;

import com.sciencebot.pos.credit.internal.entities.CreditAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, Long> {

    Optional<CreditAccount> findByStoreIdAndCustomerId(Long storeId, Long customerId);

    boolean existsByStoreIdAndCustomerIdAndBalanceGreaterThan(Long storeId, Long customerId, BigDecimal balance);

    /**
     * Suma deuda en una sola sentencia atómica, respetando el cupo: dos ventas a fiado simultáneas del mismo cliente
     * no pueden pasarse del límite. Devuelve 0 filas si superaría el cupo. No usar {@code clearAutomatically}: se
     * ejecuta dentro de la transacción de la venta y soltaría sus entidades.
     */
    @Modifying
    @Query("update CreditAccount a set a.balance = a.balance + :amount, a.updatedAt = :now "
            + "where a.id = :id and a.storeId = :storeId "
            + "and (a.creditLimit is null or a.balance + :amount <= a.creditLimit)")
    int addDebt(@Param("id") Long id, @Param("storeId") Long storeId,
                @Param("amount") BigDecimal amount, @Param("now") LocalDateTime now);

    /** Baja deuda atómicamente. Devuelve 0 filas si el monto supera el saldo actual. */
    @Modifying
    @Query("update CreditAccount a set a.balance = a.balance - :amount, a.updatedAt = :now "
            + "where a.id = :id and a.storeId = :storeId and a.balance >= :amount")
    int reduceDebt(@Param("id") Long id, @Param("storeId") Long storeId,
                   @Param("amount") BigDecimal amount, @Param("now") LocalDateTime now);

    /** Saldo actual leído de la base (no del contexto de persistencia, que puede estar desactualizado). */
    @Query("select a.balance from CreditAccount a where a.id = :id and a.storeId = :storeId")
    BigDecimal findBalance(@Param("id") Long id, @Param("storeId") Long storeId);
}
