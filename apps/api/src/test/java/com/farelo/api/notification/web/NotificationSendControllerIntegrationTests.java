package com.farelo.api.notification.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.notification.Notification;
import com.farelo.api.notification.NotificationRepository;
import com.farelo.api.notification.NotificationType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for {@code POST /api/v1/notifications/{id}/send}
 * (FARELO-111), against a real PostgreSQL instance (Testcontainers) and a
 * real local HTTP stub standing in for the Meta WhatsApp Cloud API (see
 * class javadoc on {@code NotificationSenderIntegrationTests} for why a
 * JDK {@code HttpServer} stub instead of a mocked bean/new test
 * dependency). Follows the same {@code @SpringBootTest} + {@code MockMvc}
 * + Testcontainers Postgres pattern as {@code
 * NotificationControllerIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationSendControllerIntegrationTests extends AbstractIntegrationTest {

    private static HttpServer stubServer;
    private static final AtomicInteger nextResponseStatus = new AtomicInteger(200);

    static {
        try {
            stubServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            stubServer.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                int status = nextResponseStatus.get();
                byte[] response = status == 200
                        ? "{\"messages\":[{\"id\":\"wamid.TEST\"}]}".getBytes(StandardCharsets.UTF_8)
                        : "{\"error\":{\"message\":\"stub failure\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            stubServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void overrideWhatsAppProperties(DynamicPropertyRegistry registry) {
        registry.add("whatsapp.api.base-url", () -> "http://localhost:" + stubServer.getAddress().getPort());
        registry.add("whatsapp.api.phone-number-id", () -> "654321");
        registry.add("whatsapp.api.access-token", () -> "test-access-token");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void resetStubToSucceed() {
        nextResponseStatus.set(200);
    }

    @Test
    void sendReturnsSentNotificationWhenWhatsAppApiAccepts() throws Exception {
        Notification notification = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511999999999", "Seu pedido está pronto!"));
        nextResponseStatus.set(200);

        mockMvc.perform(post("/api/v1/notifications/{id}/send", notification.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notification.getId().toString()))
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void sendReturnsFailedNotificationWhenWhatsAppApiRejects() throws Exception {
        Notification notification = notificationRepository.saveAndFlush(
                new Notification(NotificationType.STOCK_LOW, "5511988888888", "Estoque baixo: Leite"));
        nextResponseStatus.set(500);

        mockMvc.perform(post("/api/v1/notifications/{id}/send", notification.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notification.getId().toString()))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void sendReturns404WhenNotificationDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/notifications/{id}/send", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

}
