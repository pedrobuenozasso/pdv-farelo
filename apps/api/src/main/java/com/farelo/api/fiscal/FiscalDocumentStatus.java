package com.farelo.api.fiscal;

/**
 * Lifecycle states of a {@link FiscalDocument}, per the prompt mestre
 * (seção 25, "NFC-e"): {@code Close Command → Payment → Fiscal Service →
 * NFC-e → SEFAZ → AUTHORIZED}, with the full state list given literally in
 * that same section: {@code PENDING}, {@code PROCESSING}, {@code
 * AUTHORIZED}, {@code REJECTED}, {@code CANCELLED}, {@code CONTINGENCY}.
 * Used verbatim — same "closed enum when the roadmap already names the full
 * set" reasoning already applied to {@code InventoryMovementType}/{@code
 * PaymentMethod} (see those classes' javadoc): the master spec names all six
 * values explicitly for this exact document type, so there is no vocabulary
 * left to invent, and a value outside this set is a bug, not a legitimate
 * state someone forgot to add.
 *
 * <p>{@code @Enumerated(EnumType.STRING)} on {@link FiscalDocument#getStatus()}
 * (never {@code ORDINAL}) — same reasoning as {@code CommandStatus}/{@code
 * OrderStatus}/{@code PrintJobStatus}: storing the ordinal would silently
 * corrupt data if this enum's declaration order ever changes.
 *
 * <p><strong>No transition logic lives here or on {@link FiscalDocument}
 * itself</strong> — this ticket (FARELO-156, "Criar FiscalDocument") only
 * models the entity shape. The roadmap names a separate, later ticket,
 * FARELO-157 ("Criar estados fiscais"), for the state-machine/transition
 * rules that decide which moves between these six values are legal and who
 * is allowed to make them (e.g. {@code PENDING → PROCESSING → AUTHORIZED}
 * vs. {@code → REJECTED}, and whatever role {@code CONTINGENCY} plays). That
 * separate numbering is itself the signal that this ticket should not guess
 * at that logic — same "entity first, behavior later" split already applied
 * to {@code PrintJob} (FARELO-071 modeled {@code PENDING}/{@code PRINTED}/
 * {@code FAILED} before FARELO-072-079 built any producing/consuming logic).
 * {@link FiscalDocument#setStatus(FiscalDocumentStatus)} is a plain,
 * unvalidated setter for exactly this reason — see that method's javadoc.
 *
 * <p>Default status for a newly-created {@link FiscalDocument} is {@code
 * PENDING} — "not yet emitted", the natural starting point of the seção 25
 * flow, and the only state that makes sense before any real emission logic
 * (Epic 12, explicitly gated on accounting validation) exists to move a
 * document forward.
 */
public enum FiscalDocumentStatus {
    PENDING,
    PROCESSING,
    AUTHORIZED,
    REJECTED,
    CANCELLED,
    CONTINGENCY
}
