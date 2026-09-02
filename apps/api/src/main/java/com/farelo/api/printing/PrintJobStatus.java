package com.farelo.api.printing;

/**
 * Lifecycle states of a {@link PrintJob}, per the prompt mestre (seção 10):
 * {@code Order criado → PrintJob PENDING → Farelo Edge Agent → impressora →
 * PRINTED}; on failure, {@code FAILED}, allowing retry.
 *
 * <p>{@link PrintJob#retry()}/{@code PrintJobService#retry(UUID)}
 * (FARELO-079) move a {@code FAILED} job back to {@code PENDING}, capped at
 * a maximum number of attempts ({@code PrintJobRetryLimitExceededException}
 * once exhausted) — see {@code PrintJobService#retry(UUID)}'s javadoc for
 * the full design rationale.
 */
public enum PrintJobStatus {
    PENDING,
    PRINTED,
    FAILED
}
