package com.farelo.api.notification.whatsapp;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link WhatsAppCloudApiClient} against a real local HTTP server
 * — not a mocked HTTP call (e.g. Mockito-stubbing {@code RestClient}
 * itself, which would only prove the mock returns what it was told to)
 * — so what's actually asserted is the real request this class sends
 * (method, path, header, body) and how it really reacts to a real HTTP
 * response/connection failure.
 *
 * <p>No new test dependency was added for this ({@code MockWebServer}/
 * WireMock are not already on this project's classpath — verified by
 * checking {@code pom.xml} before writing this test — so pulling either in
 * for one test class would be exactly the kind of speculative dependency
 * this ticket's brief asks to avoid). {@code com.sun.net.httpserver.HttpServer}
 * (JDK built-in, no dependency at all) is the simplest option available,
 * matching the spirit of how {@code apps/edge-agent}'s {@code
 * printerTransport.ts} tests use a real local {@code net.createServer}
 * instead of a mocked socket.
 *
 * <p>Plain JUnit, no {@code @SpringBootTest}/Postgres: this class tests the
 * HTTP adapter in isolation (it is constructed directly, the same way
 * Spring would wire it, but without needing a full application context or
 * a database for what is purely an HTTP-request-shape/error-handling
 * concern). {@code NotificationSenderIntegrationTests} covers the
 * Spring-wired, Postgres-backed orchestration on top of this.
 */
class WhatsAppCloudApiClientTests {

    private static final String PHONE_NUMBER_ID = "654321";
    private static final String ACCESS_TOKEN = "test-access-token";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsCorrectRequestAndCompletesNormallyOn2xxResponse() throws IOException {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        AtomicReference<String> capturedContentType = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        server = startStubServer(exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] response = ("{\"messaging_product\":\"whatsapp\",\"contacts\":[{\"input\":\"5511999999999\"}],"
                    + "\"messages\":[{\"id\":\"wamid.TEST123\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        WhatsAppClient client = newClient(server.getAddress().getPort());

        client.sendTextMessage("5511999999999", "Seu pedido está pronto!");

        assertThat(capturedMethod.get()).isEqualTo("POST");
        assertThat(capturedPath.get()).isEqualTo("/" + PHONE_NUMBER_ID + "/messages");
        assertThat(capturedAuthHeader.get()).isEqualTo("Bearer " + ACCESS_TOKEN);
        assertThat(capturedContentType.get()).startsWith("application/json");
        // Real Meta Cloud API request shape for a text message — see
        // WhatsAppMessageRequest's javadoc.
        assertThat(capturedBody.get())
                .contains("\"messaging_product\":\"whatsapp\"")
                .contains("\"to\":\"5511999999999\"")
                .contains("\"type\":\"text\"")
                .contains("\"body\":\"Seu pedido está pronto!\"");
    }

    @Test
    void throwsWhatsAppSendExceptionOn5xxResponse() throws IOException {
        server = startStubServer(exchange -> {
            byte[] response = "{\"error\":{\"message\":\"Internal error\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        WhatsAppClient client = newClient(server.getAddress().getPort());

        // The raw RestClientResponseException never escapes — only the
        // adapter's own WhatsAppSendException does. See
        // WhatsAppCloudApiClient#sendTextMessage's javadoc.
        assertThatThrownBy(() -> client.sendTextMessage("5511999999999", "Oi"))
                .isInstanceOf(WhatsAppSendException.class)
                .hasMessageContaining("5511999999999");
    }

    @Test
    void throwsWhatsAppSendExceptionOn4xxResponse() throws IOException {
        server = startStubServer(exchange -> {
            byte[] response = ("{\"error\":{\"message\":\"Invalid parameter\",\"code\":100}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        WhatsAppClient client = newClient(server.getAddress().getPort());

        assertThatThrownBy(() -> client.sendTextMessage("5511999999999", "Oi"))
                .isInstanceOf(WhatsAppSendException.class);
    }

    @Test
    void throwsWhatsAppSendExceptionOnConnectionFailure() throws IOException {
        // Deliberately not started/listening — a connection to this port
        // is refused, simulating the WhatsApp Cloud API (or the network
        // path to it) being unreachable.
        int unreachablePort = findFreePort();

        WhatsAppClient client = newClient(unreachablePort);

        assertThatThrownBy(() -> client.sendTextMessage("5511999999999", "Oi"))
                .isInstanceOf(WhatsAppSendException.class);
    }

    private WhatsAppClient newClient(int port) {
        return new WhatsAppCloudApiClient(
                RestClient.builder(), "http://localhost:" + port, PHONE_NUMBER_ID, ACCESS_TOKEN, 2000, 5000);
    }

    private HttpServer startStubServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer stub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        stub.createContext("/", handler);
        stub.start();
        return stub;
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

}
