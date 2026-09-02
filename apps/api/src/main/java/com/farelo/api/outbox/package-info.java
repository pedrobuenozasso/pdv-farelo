/**
 * Cross-cutting infrastructure for the Transactional Outbox pattern
 * (FARELO-060) — deliberately <strong>not</strong> a business domain (see
 * the domain table at the top of {@code docs/domain-model.md}: this
 * package is intentionally absent from it, and lives outside every
 * business-domain package such as {@code com.farelo.api.ordering}).
 *
 * <p>{@link com.farelo.api.outbox.OutboxEvent} is a durable row written in
 * the <em>same transaction</em> as a domain write (see {@link
 * com.farelo.api.outbox.OutboxPublisher}), and later drained by a polling
 * worker ({@link com.farelo.api.outbox.OutboxWorker}). This is the
 * mechanism {@code docs/architecture.md} refers to as "Eventos internos de
 * domínio via Transactional Outbox + Worker, antes de introduzir um broker
 * externo (sem Kafka neste momento)."
 *
 * <p><strong>Dependency direction</strong>: business domains (e.g.
 * {@code com.farelo.api.ordering}) depend on this package to publish
 * events; this package never depends back on a domain package. The shape
 * of a given event's payload (e.g. {@code
 * com.farelo.api.ordering.OrderCreatedEvent}) lives in the domain that
 * produces it, not here — this package only knows how to durably store and
 * poll opaque {@code (aggregateType, aggregateId, eventType, payload)}
 * rows, which is what keeps it generic across every future producer.
 *
 * <p>See {@code docs/domain-model.md}'s "Outbox" section for the full
 * writeup.
 */
package com.farelo.api.outbox;
