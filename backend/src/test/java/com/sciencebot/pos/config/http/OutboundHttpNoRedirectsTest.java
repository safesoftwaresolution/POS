package com.sciencebot.pos.config.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Con una URL ya validada contra una lista de hosts, un 302 no puede llevar la peticion a otro lado. */
class OutboundHttpNoRedirectsTest {

    private HttpServer server;
    private final AtomicInteger followed = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/a", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://localhost:" + port + "/b");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/b", exchange -> {
            followed.incrementAndGet();
            byte[] body = "followed".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    @Test
    void theNoRedirectsClientReturnsTheRedirectAsIsAndNeverCallsTheTarget() {
        int status = OutboundHttp.noRedirectsRestClient().get().uri(uri("/a")).exchange((request, response) -> response.getStatusCode().value());

        assertEquals(302, status);
        assertEquals(0, followed.get());
    }

    @Test
    void theDefaultClientStillFollowsRedirectsSoFactusIsUnchanged() {
        int status = OutboundHttp.restClient().get().uri(uri("/a")).exchange((request, response) -> response.getStatusCode().value());

        assertEquals(200, status);
        assertEquals(1, followed.get());
    }

    @Test
    /** Caracterizacion: hoy Spring no pone la consulta (que puede ir firmada) en el error de E/S; si cambia, este test avisa. */
    void springDoesNotPutTheQueryStringInIoErrorMessages() {
        try {
            OutboundHttp.noRedirectsRestClient().get().uri(URI.create("http://localhost:1/x?token=SECRET")).retrieve().body(String.class);
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            assertFalse(ex.getMessage().contains("token=SECRET"), ex.getMessage());
        }
    }
}
