package com.farelo.api.printing.web;

import com.farelo.api.printing.PrintJob;
import com.farelo.api.printing.PrintJobContent;
import com.farelo.api.printing.PrintJobStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response body for {@code GET /api/v1/print-jobs} (FARELO-076) — the JPA
 * entity is never exposed directly (see AGENTS.md).
 *
 * <p><strong>{@code content} is the parsed object, not the raw JSON
 * string</strong>: {@link PrintJob#getContent()} stores a serialized
 * {@link PrintJobContent} snapshot (see that entity's javadoc, "Storage")
 * as a plain {@code String} — returning it as-is here would force the Edge
 * Agent to parse an escaped JSON string nested inside the response JSON
 * (double-parsing). Deserializing it back into {@link PrintJobContent}
 * before building this record lets Jackson serialize it as a normal nested
 * object instead, and reuses the exact record ({@code commandNumber},
 * {@code productionStation}, {@code items}) that {@link
 * com.farelo.api.printing.PrintJobService} already builds and persists —
 * no separate/duplicated shape to keep in sync. {@code PrintJobContent}
 * lives in {@code com.farelo.api.printing} (not {@code .web}), so this is
 * an ordinary same-domain, cross-subpackage reference — no dependency
 * concern (unlike reaching into a different domain's package).
 *
 * <p><strong>{@code retryCount}</strong> (FARELO-079): how many times this
 * job has been moved back from {@code FAILED} to {@code PENDING} via
 * {@code POST /api/v1/print-jobs/{id}/retry} — surfaced here so a caller
 * (e.g. a future Admin screen) can tell how close a job is to {@code
 * PrintJobService#MAX_RETRY_COUNT} without a separate lookup.
 */
public record PrintJobResponse(
        UUID id,
        UUID orderId,
        PrintJobContent content,
        PrintJobStatus status,
        int retryCount,
        OffsetDateTime createdAt) {

    public static PrintJobResponse from(PrintJob job, ObjectMapper objectMapper) {
        return new PrintJobResponse(
                job.getId(),
                job.getOrder().getId(),
                deserializeContent(job, objectMapper),
                job.getStatus(),
                job.getRetryCount(),
                job.getCreatedAt());
    }

    private static PrintJobContent deserializeContent(PrintJob job, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(job.getContent(), PrintJobContent.class);
        } catch (JsonProcessingException e) {
            // content was written by PrintJobService#serialize from a real
            // PrintJobContent instance at job-creation time — reaching here
            // means the persisted JSON no longer matches that shape, an
            // invariant violation rather than an expected runtime condition
            // (same reasoning as PrintJobService#serialize's own catch).
            throw new IllegalStateException("Failed to parse print job content for job " + job.getId(), e);
        }
    }

}
