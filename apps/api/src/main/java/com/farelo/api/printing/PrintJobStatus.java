package com.farelo.api.printing;

/**
 * Lifecycle states of a {@link PrintJob}, per the prompt mestre (seção 10):
 * {@code Order criado → PrintJob PENDING → Farelo Edge Agent → impressora →
 * PRINTED}; on failure, {@code FAILED}, allowing retry.
 *
 * <p>No retry mechanism exists yet (e.g. a way to move a {@code FAILED} job
 * back to {@code PENDING}) — that's future work once a real Edge Agent
 * reporting flow exists to drive it; this ticket (FARELO-071) only models
 * the three states themselves.
 */
public enum PrintJobStatus {
    PENDING,
    PRINTED,
    FAILED
}
