package com.farelo.api.notification;

import com.farelo.api.inventory.StockThresholdEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Builds and persists the {@code PENDING} {@link Notification} for a
 * {@code STOCK_LOW}/{@code OUT_OF_STOCK} outbox event (FARELO-113). Called
 * by {@code com.farelo.api.outbox.OutboxWorker} when it dispatches either
 * event type — see that class's javadoc for the dispatch mechanism, and
 * {@code com.farelo.api.inventory.InventoryMovementService#publishStockThresholdEventIfNeeded}
 * for where those events are published. Direct precedent: {@link
 * OrderReadyNotificationService} (FARELO-112) — same shape (a small
 * {@code @Service} that {@code OutboxWorker#dispatch} calls for one event
 * type, which creates a {@code PENDING} {@code Notification} and never
 * sends it — that's {@code NotificationWorker}'s job, unaffected by this
 * ticket), with three judgment calls this class's own event genuinely
 * needed and {@code OrderReadyNotificationService}'s didn't.
 *
 * <h2>Design decision — who gets notified: a new configuration property</h2>
 *
 * Unlike {@code ORDER_READY} (addressed to {@code Order.customerPhone}, a
 * real customer), a stock alert has no customer to notify — it's an
 * internal, staff-facing alert (prompt mestre seção 19: "Notificações
 * internas também poderão existir: estoque baixo, estoque zerado, falha de
 * impressão."). {@link NotificationType}'s own javadoc anticipates this
 * trio but never says who receives them, and nothing else in this codebase
 * models "the staff/owner's WhatsApp number" — no config property, no
 * admin-contact entity, no per-user notification preference (that would be
 * {@code security.User}'s concern, and no such field exists there either).
 * There being no other source to read this from is not a gap this ticket
 * can defer — a {@code Notification} row requires a non-null {@code
 * recipient} to even construct. The only real option is a single,
 * deployment-wide config property: {@code notification.internal-alert-recipient}
 * (see {@code application.yml}, same {@code ${ENV_VAR:default}} pattern as
 * {@code whatsapp.api.access-token} etc., empty by default — no real staff
 * number exists in dev). This is a genuine, narrow judgment call this
 * ticket has to make, not a pre-existing decision it merely applies: a
 * per-user/role recipient list, an escalation policy, or multiple
 * recipients are all plausible <em>future</em> shapes, but none of them has
 * a real requirement to design against yet (no numbered ticket asks for
 * one) — a single flat string is the smallest thing that lets {@code
 * STOCK_LOW}/{@code OUT_OF_STOCK} actually reach a human today, same YAGNI
 * discipline already applied throughout {@code inventory} (e.g. {@code
 * criticalStock} left out of FARELO-100/101).
 *
 * <p><strong>An unconfigured recipient is treated the same way {@code
 * OrderReadyNotificationService} treats a missing {@code customerPhone}:
 * not a failure.</strong> {@link #createForThresholdEvent(NotificationType,
 * String)} returns {@link Optional#empty()} and creates no {@link
 * Notification} row when the property is blank, rather than throwing —
 * throwing here would roll back the <em>entire</em> outbox batch currently
 * being drained (see {@code OutboxWorker}'s javadoc, "Failure handling"),
 * for a condition that is a deployment/config gap, not an error in the
 * stock movement that triggered this event; that movement's own transaction
 * already committed earlier, in {@code InventoryMovementService}, and must
 * never be affected by whether ops has wired up a staff WhatsApp number
 * yet. Logged at {@code WARN} rather than {@code INFO} (unlike {@code
 * OrderReadyNotificationService}'s equivalent branch): a customer not
 * leaving a phone number is an expected, everyday occurrence; a deployment
 * with no configured internal-alert recipient is a configuration gap
 * worth being noisy about, even though it must not block anything.
 *
 * <h2>Design decision — content is built from the event payload, not a
 * re-fetched {@code Ingredient}</h2>
 *
 * {@code OrderReadyNotificationService} deliberately re-fetches {@code
 * Order} from the database instead of trusting {@code OrderReadyEvent}'s
 * payload, precisely so its message always reflects the customer fields
 * <em>as of dispatch time</em>, not a possibly-stale copy carried in the
 * event. That reasoning does not carry over here. A stock alert exists to
 * describe <strong>the balance that triggered this specific event</strong>
 * — {@code StockThresholdEvent.balance()} is a snapshot of the exact ledger
 * sum {@code InventoryMovementService#publishStockThresholdEventIfNeeded}
 * computed immediately after writing the movement that crossed the
 * threshold (see that method's own javadoc). Re-fetching {@code Ingredient}
 * and recomputing the balance at dispatch time would not be "fresher" the
 * way re-fetching {@code Order} is — it would very plausibly show a
 * <em>different</em> number, if any further movements were recorded for
 * the same ingredient between publish and dispatch (the outbox is drained
 * on a poll cycle, not instantly), producing a message that no longer
 * describes the event it claims to be about ("estoque baixo" citing a
 * balance the ingredient isn't even at anymore). Using the event's own
 * snapshot is therefore the more correct choice here, not a shortcut: this
 * class reads {@code ingredientId}/{@code ingredientName}/{@code unit}/
 * {@code balance}/{@code minimumStock} straight off the deserialized {@link
 * StockThresholdEvent} and never touches {@code IngredientRepository} or
 * {@code InventoryMovementRepository} at all.
 *
 * <p>One consequence: this class depends forward on {@code
 * com.farelo.api.inventory.StockThresholdEvent} (a plain record, not the
 * {@code Ingredient} entity or any repository) to know the payload's shape.
 * {@code com.farelo.api.outbox.OutboxWorker} stays payload-agnostic despite
 * this — it hands this class the event's raw, still-serialized JSON
 * string (see {@code OutboxEvent#getPayload()}) and lets this class
 * deserialize it with its own injected {@code ObjectMapper}, the same
 * "payload shape belongs to whoever actually reads it" split {@code
 * com.farelo.api.printing.web.PrintJobResponse#deserializeContent} already
 * uses for {@code PrintJobContent} — outbox's own package-info documents
 * that {@code OutboxEvent} itself "has no opinion on what shape the
 * payload is"; this keeps that true of {@code OutboxWorker}'s dispatch too,
 * not just of the entity.
 *
 * <h2>Design decision — {@code STOCK_LOW}/{@code OUT_OF_STOCK} map 1:1 to
 * the identically-named {@link NotificationType} values</h2>
 *
 * No ambiguity here: the outbox {@code eventType} string and the {@code
 * NotificationType} enum constant are spelled identically for both cases.
 * {@code OutboxWorker} computes which one applies (from {@code
 * event.getEventType()}) and passes it in — this class does not re-derive
 * it from the payload, since {@link StockThresholdEvent} itself carries no
 * severity field (see its javadoc: severity is which outbox {@code
 * eventType} was published, decided once by {@code
 * InventoryMovementService#publishStockThresholdEventIfNeeded}).
 *
 * <h2>Design decision — message content</h2>
 *
 * A short, formatted Portuguese message referencing the ingredient name,
 * unit, and current balance (both event types), plus the configured
 * minimum for {@code STOCK_LOW} — the same "snapshot congelado em texto
 * plano" shape {@link Notification#getContent()} already documents. {@code
 * minimumStock} is expected non-null whenever {@code type} is {@code
 * STOCK_LOW} (see {@code IngredientBalance#isBelowMinimum()}'s javadoc:
 * that check itself requires a configured threshold), but this method
 * still guards against a null one defensively rather than assuming it —
 * cheap to do, and this class has no way to enforce that invariant from
 * its own side of the boundary.
 */
@Service
public class StockThresholdNotificationService {

    private static final Logger log = LoggerFactory.getLogger(StockThresholdNotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final String internalAlertRecipient;

    public StockThresholdNotificationService(
            NotificationRepository notificationRepository,
            ObjectMapper objectMapper,
            @Value("${notification.internal-alert-recipient:}") String internalAlertRecipient) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
        this.internalAlertRecipient = internalAlertRecipient;
    }

    /**
     * Creates and persists a {@code PENDING} {@link Notification} of type
     * {@code type} (expected to be {@link NotificationType#STOCK_LOW} or
     * {@link NotificationType#OUT_OF_STOCK} — see class javadoc, "1:1
     * mapping") addressed to the configured {@code
     * notification.internal-alert-recipient} — or, if that property is
     * blank, creates nothing and returns {@link Optional#empty()} (see
     * class javadoc, "who gets notified"). Does <strong>not</strong> send
     * the notification — that's {@code NotificationSender}/{@code
     * NotificationWorker}'s job, running on its own schedule, independent
     * of this write.
     *
     * @param type        the {@link NotificationType} to create — {@code
     *                    OutboxWorker} decides this from the outbox {@code
     *                    eventType} it dispatched
     * @param payloadJson the outbox event's raw JSON payload (see {@code
     *                    OutboxEvent#getPayload()}), deserialized here into
     *                    {@link StockThresholdEvent}
     * @throws IllegalStateException if {@code payloadJson} doesn't
     *         deserialize into {@link StockThresholdEvent} — not expected
     *         in practice (every {@code STOCK_LOW}/{@code OUT_OF_STOCK}
     *         event is published from {@code StockThresholdEvent.from(...)}
     *         by {@code InventoryMovementService}), same "invariant
     *         violation, not an expected runtime condition" posture as
     *         {@code PrintJobResponse#deserializeContent}'s identical catch
     */
    @Transactional
    public Optional<Notification> createForThresholdEvent(NotificationType type, String payloadJson) {
        if (internalAlertRecipient == null || internalAlertRecipient.isBlank()) {
            log.warn("{} outbox event dispatched with no notification.internal-alert-recipient configured "
                    + "— skipping internal stock alert.", type);
            return Optional.empty();
        }

        StockThresholdEvent event = deserialize(payloadJson);
        Notification notification = notificationRepository.save(
                new Notification(type, internalAlertRecipient, buildContent(type, event)));
        return Optional.of(notification);
    }

    private StockThresholdEvent deserialize(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, StockThresholdEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize StockThresholdEvent payload", e);
        }
    }

    private String buildContent(NotificationType type, StockThresholdEvent event) {
        String name = event.ingredientName();
        String unit = event.unit().name();
        String balance = event.balance().toPlainString();

        if (type == NotificationType.OUT_OF_STOCK) {
            return "Estoque zerado: " + name + " está em " + balance + " " + unit
                    + ". Reposição necessária o quanto antes.";
        }

        String minimum = event.minimumStock() != null ? event.minimumStock().toPlainString() : "não configurado";
        return "Estoque baixo: " + name + " está em " + balance + " " + unit
                + ", abaixo do mínimo configurado (" + minimum + " " + unit + ").";
    }

}
