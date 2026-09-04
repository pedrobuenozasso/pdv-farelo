package com.farelo.api.command;

import java.math.BigDecimal;

/**
 * FARELO-143 ("Validar total pago antes de fechar"): thrown when an attempt
 * to close a {@link Command} finds its total paid (sum of every {@code
 * Payment} recorded against it — {@code
 * com.farelo.api.payment.PaymentService#getTotalPaid(int)}, FARELO-142) is
 * less than its total owed (sum of {@code unitPrice * quantity} across
 * every non-cancelled order's items — {@code
 * com.farelo.api.ordering.OrderService#getTotalOwed(int)}, new in this
 * ticket). Thrown by {@code
 * com.farelo.api.payment.PaymentService#closeCommand(int)}, the orchestrating
 * method that has become the real entry point behind {@code POST
 * /api/v1/commands/{number}/close} as of this ticket — see that method's
 * javadoc for the full comparison/dependency-direction reasoning.
 *
 * <p><b>Deliberately a sibling of {@link CommandCannotBeClosedException},
 * not a reuse of it.</b> Checked before deciding this, not assumed: that
 * exception's message ({@code "cannot be closed (current status: %s,
 * expected OPEN or PAYMENT_REQUESTED)"}) is entirely about {@code status}.
 * A comanda failing <em>this</em> check is, by construction, already in a
 * closable status ({@code PaymentService#closeCommand} validates status via
 * {@link CommandService#findClosable(int)} first, and only then checks
 * payment) — so reusing {@code CommandCannotBeClosedException} here would
 * report "expected OPEN or PAYMENT_REQUESTED" for a comanda that already
 * <em>is</em> one of those, describing a problem that doesn't exist and
 * hiding the real one (insufficient payment). Same "one exception per
 * distinct failure reason" precedent {@link
 * CommandCannotAcceptPaymentsException}'s javadoc already established for
 * the analogous choice on {@code record()} — not reusing {@link
 * CommandNotAvailableException} there for the same kind of reason.
 *
 * <p>Carries both amounts ({@link #getTotalOwed()}/{@link #getTotalPaid()})
 * in addition to the formatted message, same shape as {@link
 * CommandCannotBeClosedException} carrying {@code currentStatus} — lets a
 * caller (or a future richer error response) inspect the numbers without
 * re-parsing the message string.
 */
public class CommandNotFullyPaidException extends RuntimeException {

    private final int number;
    private final BigDecimal totalOwed;
    private final BigDecimal totalPaid;

    public CommandNotFullyPaidException(int number, BigDecimal totalOwed, BigDecimal totalPaid) {
        super("Command %d cannot be closed: total paid (%s) is less than total owed (%s)"
                .formatted(number, totalPaid, totalOwed));
        this.number = number;
        this.totalOwed = totalOwed;
        this.totalPaid = totalPaid;
    }

    public int getNumber() {
        return number;
    }

    public BigDecimal getTotalOwed() {
        return totalOwed;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid;
    }

}
