package com.farelo.api.notification.whatsapp;

/**
 * The one thing FARELO-111 actually needs: a way to hand a plain-text
 * message to some outbound WhatsApp channel and find out whether it went
 * through. {@link WhatsAppCloudApiClient} is the only production
 * implementation (the Meta WhatsApp Cloud API), but the seam is kept as an
 * interface — not because a second real implementation is expected any time
 * soon (YAGNI would reject that), but because it is exactly what makes
 * {@link com.farelo.api.notification.NotificationSender} testable without a
 * live Meta account: tests can swap in a trivial hand-written fake instead
 * of standing up a mock HTTP server every time the orchestration logic
 * (mark {@code SENT}/{@code FAILED}, save) is what's under test — see
 * {@code NotificationSenderTests}.
 *
 * <p>One method, {@link #sendTextMessage}, because that's the only message
 * shape {@link com.farelo.api.notification.Notification#getContent()}
 * produces today (a single already-formatted plain-text body — see that
 * class's javadoc, "Design decision 3"). The WhatsApp Cloud API supports
 * richer message types (templates, media, interactive buttons) that this
 * interface deliberately does not model — nothing in this codebase
 * constructs a {@code Notification} that would need them yet.
 */
public interface WhatsAppClient {

    /**
     * Sends {@code messageBody} to {@code recipient} (a WhatsApp-formatted
     * phone number, e.g. {@code "5511999999999"} — see {@code
     * Notification#getRecipient()}).
     *
     * @throws WhatsAppSendException if the message could not be confirmed
     *         delivered to the channel — a network failure, a timeout, or a
     *         non-2xx response from the API. Never lets a lower-level
     *         exception (e.g. {@code RestClientException}) escape directly;
     *         see {@link WhatsAppCloudApiClient#sendTextMessage} for why.
     */
    void sendTextMessage(String recipient, String messageBody);

}
