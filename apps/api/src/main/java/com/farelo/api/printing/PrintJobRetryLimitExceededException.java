package com.farelo.api.printing;

import java.util.UUID;

/**
 * Thrown by {@link PrintJobService#retry(UUID)} (FARELO-079) when a {@link
 * PrintJob} has already been retried {@code
 * PrintJobService#MAX_RETRY_COUNT} times — the job stays {@code FAILED}
 * rather than being moved back to {@code PENDING} again.
 *
 * <p>Deliberately a distinct type from {@link
 * PrintJobInvalidTransitionException}, even though both are reported as
 * {@code 409 Conflict}: the two describe different problems for a caller
 * (e.g. a human in the Admin) to act on. An invalid-transition conflict
 * means "this job isn't {@code FAILED}, there's nothing to retry" — trying
 * again later (once/if the job does fail) may well succeed. A retry-limit
 * conflict means "this job is {@code FAILED} and eligible in principle, but
 * has already been retried the maximum number of times" — retrying again
 * will never succeed through this endpoint; the caller needs a different
 * remedy (e.g. investigate the printer, or accept the ticket won't print).
 * Collapsing both into one exception/code would hide that distinction from
 * the caller.
 */
public class PrintJobRetryLimitExceededException extends RuntimeException {

    private final UUID printJobId;
    private final int retryCount;
    private final int maxRetryCount;

    public PrintJobRetryLimitExceededException(UUID printJobId, int retryCount, int maxRetryCount) {
        super("Print job %s has already been retried %d time(s), the maximum allowed (%d) — it cannot be retried again"
                .formatted(printJobId, retryCount, maxRetryCount));
        this.printJobId = printJobId;
        this.retryCount = retryCount;
        this.maxRetryCount = maxRetryCount;
    }

    public UUID getPrintJobId() {
        return printJobId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public int getMaxRetryCount() {
        return maxRetryCount;
    }

}
