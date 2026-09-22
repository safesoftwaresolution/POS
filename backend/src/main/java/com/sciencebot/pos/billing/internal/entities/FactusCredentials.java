package com.sciencebot.pos.billing.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

/**
 * Credenciales de un entorno (sandbox o producción) entregadas por Factus a un local.
 * Se usa dos veces dentro de {@link StoreBillingConfig}.
 */
@Embeddable
@Getter
@Setter
public class FactusCredentials {

    @Column(name = "client_id", length = 255)
    private String clientId;

    @Convert(converter = SecretCryptoConverter.class)
    @Column(name = "client_secret", length = 1024)
    private String clientSecret;

    @Column(name = "username", length = 255)
    private String username;

    @Convert(converter = SecretCryptoConverter.class)
    @Column(name = "password", length = 1024)
    private String password;

    @Column(name = "numbering_range_id")
    private Integer numberingRangeId;

    /** Tiene todo lo necesario para pedir un token. El rango de numeración no cuenta aquí. */
    public boolean isComplete() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(username) && notBlank(password);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
