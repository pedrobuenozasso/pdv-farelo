package com.farelo.api.notification;

import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Notification} maps correctly onto the table created
 * by {@code V22__create_notification_table.sql}, against a real PostgreSQL
 * instance.
 *
 * <p>No {@code @BeforeEach} table cleanup here (unlike the controller test
 * in {@code .web}): every assertion below either looks up a row by its own
 * id or uses {@code contains}/{@code doesNotContain} against a known subset,
 * so leftover rows from other test methods (this class doesn't roll back
 * between tests, same as {@code PrintJobRepositoryIntegrationTests}) can't
 * make an assertion pass or fail incorrectly.
 */
@SpringBootTest
class NotificationRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void savesAndFindsNotification() {
        Notification notification = new Notification(
                NotificationType.ORDER_READY, "5511999999999", "Seu pedido está pronto!");

        Notification saved = notificationRepository.saveAndFlush(notification);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Notification> found = notificationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getType()).isEqualTo(NotificationType.ORDER_READY);
        assertThat(found.get().getRecipient()).isEqualTo("5511999999999");
        assertThat(found.get().getContent()).isEqualTo("Seu pedido está pronto!");
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void markSentAndMarkFailedTransitionStatus() {
        Notification sentNotification = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511999999999", "Pronto!"));
        sentNotification.markSent();
        notificationRepository.saveAndFlush(sentNotification);

        assertThat(notificationRepository.findById(sentNotification.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);

        Notification failedNotification = notificationRepository.saveAndFlush(
                new Notification(NotificationType.STOCK_LOW, "5511988888888", "Estoque baixo: Leite"));
        failedNotification.markFailed();
        notificationRepository.saveAndFlush(failedNotification);

        assertThat(notificationRepository.findById(failedNotification.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void findsPendingNotificationsOrderedByCreatedAtAsc() throws InterruptedException {
        Notification first = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511911111111", "Primeiro"));
        // Distinct, increasing createdAt for a deterministic FIFO assertion —
        // same pattern already used by PrintJobControllerIntegrationTests'
        // ordering test.
        Thread.sleep(10);
        Notification second = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511922222222", "Segundo"));
        Notification alreadySent = notificationRepository.saveAndFlush(
                new Notification(NotificationType.ORDER_READY, "5511933333333", "Já enviado"));
        alreadySent.markSent();
        notificationRepository.saveAndFlush(alreadySent);

        List<Notification> pending =
                notificationRepository.findByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING);

        assertThat(pending.stream().map(Notification::getId).toList())
                .contains(first.getId(), second.getId())
                .doesNotContain(alreadySent.getId());
        assertThat(pending.indexOf(first)).isLessThan(pending.indexOf(second));
    }

}
