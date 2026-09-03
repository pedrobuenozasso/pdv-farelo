package com.farelo.api.notification;

import com.farelo.api.AbstractIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies {@link NotificationSender} end to end: a Spring-wired {@link
 * NotificationSender} backed by the real {@code
 * com.farelo.api.notification.whatsapp.WhatsAppCloudApiClient} bean
 * (pointed at a local HTTP stub via {@code whatsapp.api.base-url}, not a
 * mocked bean) and a real {@link NotificationRepository} against
 * PostgreSQL (Testcontainers, via {@link AbstractIntegrationTest}) — proves
 * the whole "send, then persist the outcome" cycle this ticket's brief
 * asks for, not just the HTTP layer in isolation ({@code
 * WhatsAppCloudApiClientTests} covers that).
 *
 * <p>The stub server is started once per test class (a plain instance
 * field would be re-created per test method, but {@code
 * @DynamicPropertySource} methods run once, statically, before any test
 * method — the server has to exist by then). Its behavior for the next
 * request is set per test via {@link #nextResponseStatus}, safe because
 * — per {@code AbstractIntegrationTest}'s own javadoc — test classes in
 * this suite run sequentially, never concurrently.
 */
@SpringBootTest
class NotificationSenderIntegrationTests extends AbstractIntegrationTest {

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
    private NotificationSender notificationSender;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void resetStubToSucceed() {
        nextResponseStatus.set(200);
    }

    @Test
    void successfulSendMarksNotificationSent() {
        Notification notification = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511999999999", "Seu pedido está pronto!"));
        nextResponseStatus.set(200);

        Notification result = notificationSender.send(notification);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void failedSendMarksNotificationFailedAndDoesNotThrow() {
        Notification notification = notificationRepository.saveAndFlush(
                new Notification(NotificationType.STOCK_LOW, "5511988888888", "Estoque baixo: Leite"));
        nextResponseStatus.set(500);

        Notification result = notificationSender.send(notification);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void sendByIdThrowsNotificationNotFoundExceptionForUnknownId() {
        UUID unknownId = UUID.randomUUID();

        NotificationNotFoundException exception = assertThrows(
                NotificationNotFoundException.class, () -> notificationSender.sendById(unknownId));

        assertThat(exception.getNotificationId()).isEqualTo(unknownId);
    }

    @Test
    void sendByIdLooksUpAndSendsExistingNotification() {
        Notification notification = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511977777777", "Pedido pronto"));
        nextResponseStatus.set(200);

        Notification result = notificationSender.sendById(notification.getId());

        assertThat(result.getId()).isEqualTo(notification.getId());
        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

}
