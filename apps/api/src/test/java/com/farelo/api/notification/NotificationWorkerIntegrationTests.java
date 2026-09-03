package com.farelo.api.notification;

import com.farelo.api.AbstractIntegrationTest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link NotificationWorker#processPendingNotifications()} directly
 * (FARELO-112): creates {@code PENDING} {@link Notification} rows straight
 * via {@link NotificationRepository} (bypassing {@code
 * OrderReadyNotificationService}/outbox entirely — that creation path is
 * covered by {@code OrderReadyNotificationServiceIntegrationTests}/{@code
 * OutboxWorkerOrderReadyIntegrationTests}) so this class can focus purely on
 * the poll-and-send loop: every {@code PENDING} row gets attempted, each
 * independently, regardless of whether its neighbor in the same batch
 * succeeds or fails.
 *
 * <p>Same local-HTTP-stub-instead-of-mocked-bean approach as {@code
 * NotificationSenderIntegrationTests} — this class exercises the real
 * Spring-wired {@code WhatsAppCloudApiClient} bean, pointed at its own stub
 * server instance (a fresh {@code @DynamicPropertySource}-backed Spring
 * context, same pattern already used by {@code
 * OutboxWorkerBatchSizeIntegrationTests} for a property override scoped to
 * one test class).
 */
@SpringBootTest
class NotificationWorkerIntegrationTests extends AbstractIntegrationTest {

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
    private NotificationWorker notificationWorker;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void resetStubToSucceed() {
        nextResponseStatus.set(200);
    }

    // Found in review (FARELO-112): this class's assertions on
    // processPendingNotifications()'s exact batch size only hold if the
    // notification table is empty when each test starts — {@link
    // NotificationWorker#processPendingNotifications} deliberately has no
    // per-caller scoping (it drains every PENDING row system-wide, by
    // design). Other test classes sharing the singleton Postgres container
    // (e.g. NotificationControllerIntegrationTests, which only clears the
    // table in its own @BeforeEach, not after its last test) can leave
    // PENDING rows behind depending on Surefire's actual run order — this
    // class previously cleaned up only in @AfterEach, which does nothing
    // for whatever was already there before this class's first test runs.
    // Cleaning both before and after closes that gap without relying on
    // run order.
    @BeforeEach
    void cleanUpBeforeEach() {
        notificationRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
    }

    @Test
    void sendsEveryPendingNotificationAndPersistsSentStatus() {
        Notification first = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511999999991", "Pedido pronto 1"));
        Notification second = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511999999992", "Pedido pronto 2"));

        List<Notification> processed = notificationWorker.processPendingNotifications();

        assertThat(processed).hasSize(2);
        assertThat(notificationRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
        assertThat(notificationRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void aFailedSendDoesNotAbortTheRestOfTheBatch() {
        // First notification will fail (stub set to 500 before this one is
        // attempted); the loop must still go on to attempt the second one.
        Notification willFail = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511999999993", "Pedido pronto 3"));
        nextResponseStatus.set(500);

        List<Notification> processed = notificationWorker.processPendingNotifications();
        assertThat(processed).hasSize(1);
        assertThat(notificationRepository.findById(willFail.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.FAILED);

        nextResponseStatus.set(200);
        Notification willSucceed = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511999999994", "Pedido pronto 4"));

        List<Notification> secondBatch = notificationWorker.processPendingNotifications();
        // Only the new PENDING one — the earlier one already ended FAILED,
        // a terminal-for-this-poll status that listPending() no longer
        // returns (see NotificationSender's javadoc: FAILED can still be
        // re-sent manually, but the worker only ever picks up PENDING).
        assertThat(secondBatch).extracting(Notification::getId).containsExactly(willSucceed.getId());
        assertThat(notificationRepository.findById(willSucceed.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void returnsEmptyListWhenNothingIsPending() {
        List<Notification> processed = notificationWorker.processPendingNotifications();

        assertThat(processed).isEmpty();
    }

}
