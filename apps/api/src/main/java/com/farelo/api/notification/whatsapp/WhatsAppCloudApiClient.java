package com.farelo.api.notification.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The real, production {@link WhatsAppClient} — talks to the Meta WhatsApp
 * Cloud API (prompt mestre seção 19: "Utilizar futuramente: Meta WhatsApp
 * Cloud API") over plain HTTP using Spring's {@link RestClient}.
 *
 * <h2>Why {@code RestClient}, not {@code WebClient}/{@code RestTemplate}</h2>
 *
 * This is the first outbound HTTP client this codebase has ever needed —
 * there was no existing precedent to follow ({@code grep}-ing the whole
 * {@code apps/api} tree for {@code RestTemplate}/{@code WebClient}/{@code
 * RestClient}/{@code HttpClient} usage turns up nothing before this
 * ticket). {@code RestClient} was chosen over the alternatives because: (a)
 * this application is a plain Spring MVC (servlet) app, not WebFlux — {@code
 * spring-webflux} isn't even on the classpath, so {@code WebClient} would
 * pull in a whole reactive stack for exactly one blocking call-and-wait use
 * case, which is what this is (a synchronous "send and record the outcome"
 * cycle, per this ticket's scope — nothing here needs non-blocking I/O);
 * (b) {@code RestTemplate} is in Spring's own maintenance-mode territory
 * going forward, and {@code RestClient} (Spring Framework 6.1+, so already
 * transitively on this project's classpath via {@code
 * spring-boot-starter-web} — no new dependency needed) is its designed
 * successor with the same synchronous, imperative call style this one-shot
 * use case wants.
 *
 * <h2>Failure handling — the "no failure crashes the process" contract</h2>
 *
 * Every failure mode {@code RestClient} can throw for this call —
 * connection refused, DNS failure, a timeout ({@code
 * ResourceAccessException}), or a non-2xx response ({@code
 * RestClientResponseException}, thrown by {@code retrieve()}'s default
 * status handler) — is a subtype of {@link RestClientException}. This
 * method catches that one common supertype and rewraps it as {@link
 * WhatsAppSendException}, so {@link
 * com.farelo.api.notification.NotificationSender} (this adapter's only
 * caller) never has to know or guess which of Spring's several HTTP
 * exception types it might see — it only needs the one signal "delivery
 * failed" to mark a {@code Notification} {@code FAILED} instead of leaving
 * it {@code PENDING} or letting the exception escape uncaught. Same
 * "nothing here crashes the process" philosophy {@code apps/edge-agent}'s
 * {@code poller.ts}/{@code printOverTcp} already apply on the Edge Agent
 * side of this same WhatsApp/printing "external system might be down"
 * problem shape.
 *
 * <h2>Configuration</h2>
 *
 * All four values below come from {@code application.yml} (which reads
 * them from environment variables — same {@code ${ENV_VAR:default}} pattern
 * already used for {@code spring.datasource.*}), never hardcoded:
 *
 * <ul>
 *   <li>{@code whatsapp.api.base-url} — the Graph API root (default {@code
 *       https://graph.facebook.com/v20.0}, overridable so tests can point
 *       it at a local stub server — see {@code WhatsAppCloudApiClientTests}
 *       — instead of the real Meta endpoint).</li>
 *   <li>{@code whatsapp.api.phone-number-id} — the sender's Meta-issued
 *       phone number id, the first path segment of {@code POST
 *       /{phone-number-id}/messages}.</li>
 *   <li>{@code whatsapp.api.access-token} — sent as {@code Authorization:
 *       Bearer <token>} on every request.</li>
 *   <li>{@code whatsapp.api.connect-timeout-ms}/{@code
 *       whatsapp.api.read-timeout-ms} — bound how long one send attempt can
 *       block waiting on a possibly-unreachable external API, same
 *       "external calls must not hang forever" reasoning already applied
 *       elsewhere in this codebase's failure handling. Defaulted (5s/10s)
 *       rather than left unbounded (the JDK/Spring default), since an
 *       unbounded read timeout would defeat the very "record the outcome
 *       promptly, never leave the caller hanging" contract this class
 *       exists to provide.</li>
 * </ul>
 *
 * <p>No live Meta account, phone number id, or access token exists in this
 * dev environment (per this ticket's brief) — {@code phone-number-id}/
 * {@code access-token} default to an empty string in {@code
 * application.yml} precisely so this bean can still be constructed (and the
 * rest of the Spring context still starts) without either configured; a
 * real send attempt against the real API without them would simply come
 * back as a 4xx, caught and reported as any other {@link
 * WhatsAppSendException} — no special-casing needed here for "not
 * configured yet".
 */
@Component
public class WhatsAppCloudApiClient implements WhatsAppClient {

    private final RestClient restClient;
    private final String phoneNumberId;
    private final String accessToken;

    public WhatsAppCloudApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${whatsapp.api.base-url}") String baseUrl,
            @Value("${whatsapp.api.phone-number-id}") String phoneNumberId,
            @Value("${whatsapp.api.access-token}") String accessToken,
            @Value("${whatsapp.api.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${whatsapp.api.read-timeout-ms:10000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code POST {base-url}/{phone-number-id}/messages}, body per
     * {@link WhatsAppMessageRequest#text}, {@code Authorization: Bearer
     * {access-token}}. The response body is intentionally not parsed —
     * nothing in this ticket needs the message id Meta returns on success
     * (e.g. for later delivery-status webhooks); only whether the call
     * succeeded at all matters to {@link
     * com.farelo.api.notification.NotificationSender}. See class javadoc,
     * "Failure handling", for why every failure mode collapses to {@link
     * WhatsAppSendException}.
     */
    @Override
    public void sendTextMessage(String recipient, String messageBody) {
        try {
            restClient.post()
                    .uri("/{phoneNumberId}/messages", phoneNumberId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(WhatsAppMessageRequest.text(recipient, messageBody))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new WhatsAppSendException(
                    "Failed to send WhatsApp message to " + recipient + " via Meta Cloud API: " + e.getMessage(), e);
        }
    }

}
