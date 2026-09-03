package com.farelo.api.payment;

/**
 * How a {@link Payment} was settled.
 *
 * <p>The literal, complete list from the prompt mestre (seção 47,
 * FARELO-141: "Registrar pagamento manual. Métodos: {@code PIX}, {@code
 * CREDIT_CARD}, {@code DEBIT_CARD}, {@code CASH}, {@code OTHER}"). Same
 * situation {@link com.farelo.api.inventory.InventoryMovementType}/
 * {@code NotificationType} were already in when they were modeled as closed
 * enums: the master spec already names every value explicitly and
 * exhaustively for this exact entity, so there's nothing to "guess" a
 * design for. Contrast with {@link com.farelo.api.audit.AuditLog#getAction()},
 * which stays an open {@code String} specifically because <em>its</em>
 * vocabulary is <b>not</b> fully known upfront (see that class's javadoc,
 * "Design decision 3") — a closed enum is the wrong tool there because new
 * producers keep inventing new action names the spec never enumerated.
 * Here it's the opposite situation: five named values, no vocabulary left
 * to invent, so a closed enum is the right tool — a value outside this set
 * is a bug, not a legitimate new payment method someone forgot to add yet.
 *
 * <p>This ticket (FARELO-140) creates no producer for any of these values —
 * nothing in this codebase yet constructs a {@link Payment}. That's
 * FARELO-141 ("Registrar pagamento manual"), a future ticket.
 */
public enum PaymentMethod {
    PIX,
    CREDIT_CARD,
    DEBIT_CARD,
    CASH,
    OTHER
}
