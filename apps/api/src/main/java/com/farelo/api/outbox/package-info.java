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
 * <p><strong>Dependency direction (publishing)</strong>: business domains
 * (e.g. {@code com.farelo.api.ordering}) depend on this package to publish
 * events. The shape of a given event's payload (e.g. {@code
 * com.farelo.api.ordering.OrderCreatedEvent}) lives in the domain that
 * produces it, not here — {@link com.farelo.api.outbox.OutboxEvent} only
 * knows how to durably store and poll opaque {@code (aggregateType,
 * aggregateId, eventType, payload)} rows, which is what keeps
 * <em>publishing</em> generic across every producer.
 *
 * <p><strong>Dependency direction (dispatching, since FARELO-072)</strong>:
 * that genericity stops at publishing. {@link
 * com.farelo.api.outbox.OutboxWorker} — the consumer side — now depends
 * forward on {@code com.farelo.api.printing.PrintJobService} to actually do
 * something with an {@code OrderCreated} event (create a {@code PrintJob}).
 * This is a deliberate, narrow exception to "never depends back on a
 * domain package", not an oversight: dispatching an event to real work
 * inherently means calling into whatever domain does that work, and with
 * exactly one real consumer there is nothing to hide that call behind
 * without guessing at a plugin shape no second consumer exists yet to
 * validate (see {@code OutboxWorker}'s javadoc). If/when a second real
 * consumer appears (e.g. inventory, notification — future epics), that's
 * the point to revisit this with a proper handler-registry abstraction
 * that would restore a generic worker; forcing that shape into existence
 * for a single consumer today would be speculative.
 *
 * <p>See {@code docs/domain-model.md}'s "Outbox" section for the full
 * writeup.
 */
package com.farelo.api.outbox;
