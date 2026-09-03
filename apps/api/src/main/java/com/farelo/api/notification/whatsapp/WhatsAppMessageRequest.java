package com.farelo.api.notification.whatsapp;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body shape the Meta WhatsApp Cloud API expects for a plain-text
 * outbound message — {@code POST /{phone-number-id}/messages}:
 *
 * <pre>{@code
 * {
 *   "messaging_product": "whatsapp",
 *   "to": "5511999999999",
 *   "type": "text",
 *   "text": { "body": "..." }
 * }
 * }</pre>
 *
 * Package-private: only {@link WhatsAppCloudApiClient} builds one, and
 * nothing outside this package needs to know the Cloud API's exact wire
 * shape — same "adapter-internal DTO" reasoning as, e.g., {@code
 * com.farelo.api.printing.PrintJobContent} not leaking past its own
 * consumer.
 */
record WhatsAppMessageRequest(
        @JsonProperty("messaging_product") String messagingProduct,
        String to,
        String type,
        WhatsAppTextPayload text) {

    static WhatsAppMessageRequest text(String to, String body) {
        return new WhatsAppMessageRequest("whatsapp", to, "text", new WhatsAppTextPayload(body));
    }

    record WhatsAppTextPayload(String body) {
    }

}
