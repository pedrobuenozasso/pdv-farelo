package com.farelo.api.notification;

import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderNotFoundException;
import com.farelo.api.ordering.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Builds and persists the {@code PENDING} {@link Notification} for an
 * {@link Order} that just became {@code READY} (FARELO-112). Called by
 * {@code com.farelo.api.outbox.OutboxWorker} when it dispatches an {@code
 * OrderReady} outbox event — see that class's javadoc for the dispatch
 * mechanism, and {@code com.farelo.api.ordering.OrderService#markAsReady}
 * for where that event is published.
 *
 * <h2>Design decision — content comes from the database, not the event
 * payload</h2>
 *
 * Same reasoning as {@code com.farelo.api.printing.PrintJobService}'s own
 * "content comes from the database" decision for {@code OrderCreated} (see
 * its javadoc): {@code OrderReadyEvent} (the outbox event's payload) only
 * carries {@code orderId}/{@code commandNumber} — this class re-fetches the
 * {@link Order} straight from the database by {@code aggregateId} instead of
 * trusting the payload for anything beyond "which order". That keeps this
 * class reading whatever the order's customer fields actually are as of
 * dispatch time (not a possibly-stale copy carried in the event), and keeps
 * {@code OrderReadyEvent} free to change independent of what a notification
 * needs from it.
 *
 * <h2>Design decision — no {@code customerPhone} means no {@code
 * Notification}, and that's not a failure</h2>
 *
 * {@code Order.customerPhone} is nullable (see {@code Order}'s javadoc) — a
 * customer who didn't leave a WhatsApp number simply has no one to notify.
 * {@link #createForOrder(UUID)} treats a {@code null}/blank phone as a
 * legitimate, expected outcome: it returns {@link Optional#empty()} rather
 * than throwing, and creates no {@link Notification} row at all. This
 * matters for {@code OutboxWorker}'s failure-handling contract (see its
 * javadoc, "Failure handling") — throwing here would roll back the
 * <em>entire</em> outbox batch currently being drained, for a condition that
 * isn't actually an error. The order's {@code READY} transition itself
 * already committed in its own earlier transaction ({@code
 * OrderService#markAsReady}); this class runs later, asynchronously, and
 * must never be able to affect whether that transition succeeded — the
 * kitchen/pickup flow does not depend on whether a phone number was
 * collected.
 *
 * <h2>Design decision — message content</h2>
 *
 * A short, formatted Portuguese message referencing the comanda number (the
 * thing a customer physically holds and recognizes) and, when available, the
 * customer's name — the same "snapshot congelado em texto plano" shape
 * {@link Notification#getContent()} already documents (frozen at creation
 * time, never recomputed later). No product/item details are included: the
 * prompt mestre's {@code ORDER_READY} flow (seção 19) is about telling the
 * customer their order is ready for pickup, not reproducing a receipt.
 */
@Service
public class OrderReadyNotificationService {

    private static final Logger log = LoggerFactory.getLogger(OrderReadyNotificationService.class);

    private final OrderRepository orderRepository;
    private final NotificationRepository notificationRepository;

    public OrderReadyNotificationService(OrderRepository orderRepository, NotificationRepository notificationRepository) {
        this.orderRepository = orderRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Creates and persists a {@code PENDING}, {@link NotificationType#ORDER_READY}
     * {@link Notification} addressed to {@code order.customerPhone} — or, if
     * the order has no phone number on file, creates nothing and returns
     * {@link Optional#empty()} (see class javadoc, "no customerPhone means no
     * Notification"). Does <strong>not</strong> send the notification —
     * that's {@code NotificationSender}/{@code NotificationWorker}'s job,
     * running on its own schedule, independent of this write.
     *
     * @throws OrderNotFoundException if no order exists for {@code orderId}
     *         — not expected in practice (the order was written, and already
     *         transitioned to {@code READY}, in the transaction that
     *         published the {@code OrderReady} event this method is invoked
     *         for), but not guarded against beyond letting it propagate —
     *         same posture as {@code PrintJobService#createForOrder}.
     */
    @Transactional
    public Optional<Notification> createForOrder(UUID orderId) {
        Order order = orderRepository.findByIdWithCommand(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        String phone = order.getCustomerPhone();
        if (phone == null || phone.isBlank()) {
            log.info("Order {} became READY with no customerPhone on file — skipping ORDER_READY notification.", orderId);
            return Optional.empty();
        }

        Notification notification = notificationRepository.save(
                new Notification(NotificationType.ORDER_READY, phone, buildContent(order)));
        return Optional.of(notification);
    }

    private String buildContent(Order order) {
        int commandNumber = order.getCommand().getNumber();
        String customerName = order.getCustomerName();

        if (customerName != null && !customerName.isBlank()) {
            return "Olá, " + customerName + "! Seu pedido da comanda " + commandNumber
                    + " já está pronto para retirada. Obrigado por escolher o Farelo!";
        }
        return "Seu pedido da comanda " + commandNumber
                + " já está pronto para retirada. Obrigado por escolher o Farelo!";
    }

}
