package com.sciencebot.pos.billing.dto;

import java.util.Set;

/**
 * Estados del ciclo de vida de una Factura Electrónica ante la DIAN.
 */
public enum ElectronicInvoiceStatus {

    /** Registrada en el sistema local, pendiente de envío o procesamiento por el proveedor/DIAN. */
    PENDING,

    /** Certificada y validada exitosamente por la DIAN con asignación de CUFE y número oficial. */
    VALIDATED,

    /** Rechazada por la DIAN debido a inconsistencias tributarias o de datos. */
    REJECTED,

    /** Fallo técnico de comunicación, timeout o error interno del proveedor. */
    ERROR;

    private static final Set<ElectronicInvoiceStatus> FROM_PENDING = Set.of(VALIDATED, REJECTED, ERROR);
    private static final Set<ElectronicInvoiceStatus> FROM_ERROR = Set.of(PENDING);
    private static final Set<ElectronicInvoiceStatus> FROM_REJECTED = Set.of(PENDING);
    private static final Set<ElectronicInvoiceStatus> FROM_VALIDATED = Set.of(); // Estado final inmutable

    public boolean canTransitionTo(ElectronicInvoiceStatus target) {
        if (target == null) {
            return false;
        }
        Set<ElectronicInvoiceStatus> allowed = switch (this) {
            case PENDING -> FROM_PENDING;
            case ERROR -> FROM_ERROR;
            case REJECTED -> FROM_REJECTED;
            case VALIDATED -> FROM_VALIDATED;
        };
        return allowed.contains(target);
    }

    public boolean isTerminal() {
        return this == VALIDATED;
    }
}
