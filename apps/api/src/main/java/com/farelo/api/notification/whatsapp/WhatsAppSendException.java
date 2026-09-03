package com.farelo.api.notification.whatsapp;

/**
 * The single failure signal {@link WhatsAppClient} ever throws — a network
 * error, a timeout, or a non-2xx response from the WhatsApp Cloud API all
 * collapse into this one unchecked type (see {@link
 * WhatsAppCloudApiClient#sendTextMessage} for where each is caught and
 * rewrapped). {@link com.farelo.api.notification.NotificationSender} only
 * needs to know "delivery did not succeed" to mark a {@link
 * com.farelo.api.notification.Notification} {@code FAILED} — it has no use
 * for telling a timeout apart from a 500 apart from a DNS failure, so this
 * ticket does not invent a hierarchy of more specific subtypes for
 * distinctions nothing reads yet. The original cause is preserved (never
 * swallowed) for whatever ends up reading application logs.
 */
public class WhatsAppSendException extends RuntimeException {

    public WhatsAppSendException(String message, Throwable cause) {
        super(message, cause);
    }

    public WhatsAppSendException(String message) {
        super(message);
    }

}
