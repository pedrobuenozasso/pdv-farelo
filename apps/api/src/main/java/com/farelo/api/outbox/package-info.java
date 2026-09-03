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
 * <p><strong>Dependency direction (dispatching, since FARELO-072, extended
 * FARELO-112)</strong>: that genericity stops at publishing. {@link
 * com.farelo.api.outbox.OutboxWorker} — the consumer side — now depends
 * forward on two domain services to actually do something with an event:
 * {@code com.farelo.api.printing.PrintJobService} for {@code OrderCreated}
 * (create a {@code PrintJob}), and, since FARELO-112, {@code
 * com.farelo.api.notification.OrderReadyNotificationService} for {@code
 * OrderReady} (create a {@code PENDING Notification}, or nothing at all if
 * the order has no {@code customerPhone}). This is a deliberate, narrow
 * exception to "never depends back on a domain package", not an oversight:
 * dispatching an event to real work inherently means calling into whatever
 * domain does that work. With exactly two real consumers today, a plain
 * {@code if}/{@code else if} in {@link com.farelo.api.outbox.OutboxWorker}
 * still says everything a handler registry would, with less indirection —
 * see that class's javadoc, "Dispatch mechanism", for the full reasoning.
 * If/when a <em>third</em> real consumer appears (e.g. inventory reacting to
 * {@code OrderCreated}, or {@code STOCK_LOW}/{@code STOCK_CRITICAL}/{@code
 * OUT_OF_STOCK} feeding {@code notification} the way {@code OrderReady} does
 * today — FARELO-113 and beyond), that's the point to revisit this with a
 * proper handler-registry abstraction that would restore a generic worker;
 * forcing that shape into existence for two consumers today would still be
 * speculative.
 *
 * <p>See {@code docs/domain-model.md}'s "Outbox" section for the full
 * writeup.
 */
package com.farelo.api.outbox;
