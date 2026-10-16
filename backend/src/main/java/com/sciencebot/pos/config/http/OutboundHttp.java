package com.sciencebot.pos.config.http;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Fabrica de {@link RestClient} para integraciones externas (Factus/DIAN). {@code RestClient.create()}
 * no fija timeouts: un proveedor lento dejaba hilos, conexiones del pool y filas bloqueadas esperando
 * indefinidamente. Aqui toda llamada saliente tiene limite de conexion y de lectura.
 */
public final class OutboundHttp {

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** Emitir ante la DIAN puede tardar; se deja holgura sin permitir esperas indefinidas. */
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(45);

    private OutboundHttp() {
    }

    public static RestClient restClient() {
        return restClient(CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    public static RestClient restClient(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}
