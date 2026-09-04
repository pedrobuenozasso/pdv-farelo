package com.farelo.api.printing.web;

import com.farelo.api.printing.CommandCheckContent;
import com.farelo.api.printing.PrintJob;
import com.farelo.api.printing.PrintJobContent;
import com.farelo.api.printing.PrintJobStatus;
import com.farelo.api.printing.PrintJobType;
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
 *
 * <p><strong>{@code type}/{@code orderId}/{@code commandNumber}/{@code
 * content}/{@code commandCheckContent}</strong> (FARELO-210/211): a
 * {@link PrintJobType#KITCHEN_TICKET} job populates {@code orderId} and
 * {@code content} ({@code commandNumber}/{@code commandCheckContent} both
 * {@code null}); a {@link PrintJobType#COMMAND_CHECK} job populates {@code
 * commandNumber} and {@code commandCheckContent} instead ({@code orderId}/
 * {@code content} both {@code null}). Two separate typed fields rather
 * than one loosely-typed {@code Object content} field: it keeps every
 * existing {@code KITCHEN_TICKET} caller (Edge Agent, tests) reading
 * {@code content} exactly as before FARELO-210/211 — unaffected by the new
 * job type — instead of forcing every reader to branch on {@code type}
 * just to get back the same {@link PrintJobContent} shape they already
 * depended on.
 */
public record PrintJobResponse(
        UUID id,
        PrintJobType type,
        UUID orderId,
        Integer commandNumber,
        PrintJobContent content,
        CommandCheckContent commandCheckContent,
        PrintJobStatus status,
        int retryCount,
        OffsetDateTime createdAt) {

    public static PrintJobResponse from(PrintJob job, ObjectMapper objectMapper) {
        boolean isCommandCheck = job.getType() == PrintJobType.COMMAND_CHECK;
        return new PrintJobResponse(
                job.getId(),
                job.getType(),
                isCommandCheck ? null : job.getOrder().getId(),
                isCommandCheck ? job.getCommand().getNumber() : null,
                isCommandCheck ? null : deserialize(job, objectMapper, PrintJobContent.class),
                isCommandCheck ? deserialize(job, objectMapper, CommandCheckContent.class) : null,
                job.getStatus(),
                job.getRetryCount(),
                job.getCreatedAt());
    }

    private static <T> T deserialize(PrintJob job, ObjectMapper objectMapper, Class<T> type) {
        try {
            return objectMapper.readValue(job.getContent(), type);
        } catch (JsonProcessingException e) {
            // content was written by PrintJobService#serialize from a real
            // PrintJobContent/CommandCheckContent instance at job-creation
            // time, matching job.getType() — reaching here means the
            // persisted JSON no longer matches that shape, an invariant
            // violation rather than an expected runtime condition (same
            // reasoning as PrintJobService#serialize's own catch).
            throw new IllegalStateException("Failed to parse print job content for job " + job.getId(), e);
        }
    }

}
