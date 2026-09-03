package com.farelo.api.notification;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.inventory.IngredientUnit;
import com.farelo.api.inventory.StockThresholdEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link StockThresholdNotificationService#createForThresholdEvent}
 * directly (FARELO-113) — the payload-deserialization and content-building
 * logic, and specifically the "no recipient configured" branch, which the
 * full-pipeline test ({@code
 * com.farelo.api.outbox.OutboxWorkerStockThresholdIntegrationTests}) can't
 * exercise on its own, since it always overrides {@code
 * notification.internal-alert-recipient} via {@code @DynamicPropertySource}
 * to prove the happy path end to end. This class deliberately does
 * <b>not</b> override that property, relying on {@code application.yml}'s
 * own default (empty, see that file's FARELO-113 comment) to exercise the
 * unconfigured-recipient branch — same "no live account configured in this
 * dev environment" reasoning already established for {@code
 * whatsapp.api.access-token}.
 */
@SpringBootTest
class StockThresholdNotificationServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private StockThresholdNotificationService stockThresholdNotificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        notificationRepository.findAll().stream()
                .filter(n -> n.getContent().contains("Café em grão (FARELO-113 svc)"))
                .forEach(notificationRepository::delete);
    }

    private String payloadFor(BigDecimal balance, BigDecimal minimumStock) throws Exception {
        StockThresholdEvent event = new StockThresholdEvent(
                UUID.randomUUID(), "Café em grão (FARELO-113 svc)", IngredientUnit.GRAM, balance, minimumStock);
        return objectMapper.writeValueAsString(event);
    }

    @Test
    void createsNoNotificationWhenNoInternalAlertRecipientIsConfigured() throws Exception {
        // application.yml's own default for notification.internal-alert-recipient
        // is empty in this test context (never overridden here) — same
        // "not configured yet" state a fresh deployment would start in.
        String payload = payloadFor(new BigDecimal("400"), new BigDecimal("500"));

        Optional<Notification> result =
                stockThresholdNotificationService.createForThresholdEvent(NotificationType.STOCK_LOW, payload);

        assertThat(result).isEmpty();
        boolean anyPersisted = notificationRepository.findAll().stream()
                .anyMatch(n -> n.getContent().contains("Café em grão (FARELO-113 svc)"));
        assertThat(anyPersisted).isFalse();
    }

}
