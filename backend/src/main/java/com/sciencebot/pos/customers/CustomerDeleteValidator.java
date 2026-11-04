package com.sciencebot.pos.customers;

/**
 * Lo implementan los módulos que guardan datos que dependen de un cliente (por ejemplo, su deuda de fiado) para
 * impedir que se dé de baja mientras esos datos sigan vigentes. Lanza {@link IllegalStateException} si no se puede.
 */
public interface CustomerDeleteValidator {
    void validateBeforeDelete(Long customerId);
}
