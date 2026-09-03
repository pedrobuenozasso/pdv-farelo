package com.farelo.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farelo.api.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link AuditLog} maps correctly onto the table created by
 * {@code V25__create_audit_log_table.sql}, against a real PostgreSQL
 * instance — including that {@code previousValue}/{@code newValue} (jsonb)
 * round-trip, that both can be {@code null} independently, and that the
 * three derived finders return rows in the right order/scope.
 *
 * <p>{@code userId} below is a bare {@link UUID#randomUUID()}, never a
 * persisted {@code User} row — deliberate, and itself a small proof of
 * {@link AuditLog}'s javadoc ("Design decision 2"): {@code audit_log.user_id}
 * carries no foreign key to {@code app_user(id)}, so a row referencing a
 * user id that was never (or is no longer) a real {@code app_user} row is
 * expected to persist without error, exactly like a row for a genuinely
 * deleted account would.
 *
 * <p>No {@code @BeforeEach} table cleanup, same reasoning as {@code
 * InventoryMovementRepositoryIntegrationTests}: every test creates its own
 * fresh, randomly-generated {@code entityId}/{@code userId} and only ever
 * queries/asserts scoped to those specific ids, so leftover rows from other
 * tests sharing the singleton Postgres container (see {@link
 * AbstractIntegrationTest}) never affect an assertion here.
 */
@SpringBootTest
class AuditLogRepositoryIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void savesAndFindsAuditLogWithBothSnapshotValues() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        AuditLog auditLog = new AuditLog(
                userId, "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", entityId,
                "{\"price\":10.50}", "{\"price\":12.00}");

        AuditLog saved = auditLogRepository.saveAndFlush(auditLog);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<AuditLog> found = auditLogRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getUserName()).isEqualTo("Ana Souza");
        assertThat(found.get().getUserEmail()).isEqualTo("ana@farelo.dev");
        assertThat(found.get().getAction()).isEqualTo("PRICE_CHANGED");
        assertThat(found.get().getEntityType()).isEqualTo("Product");
        assertThat(found.get().getEntityId()).isEqualTo(entityId);
        // Compare as parsed JSON, not raw string: Postgres' jsonb column
        // normalizes key order/whitespace on round-trip (it is not a text
        // column), same gotcha already documented by
        // PrintJobRepositoryIntegrationTests for PrintJob#getContent().
        assertThatJson(found.get().getPreviousValue(), "{\"price\":10.50}");
        assertThatJson(found.get().getNewValue(), "{\"price\":12.00}");
    }

    /**
     * Compares two JSON strings by parsed value, not raw text — see the
     * comment at the call site.
     */
    private void assertThatJson(String actual, String expected) {
        try {
            assertThat(objectMapper.readTree(actual)).isEqualTo(objectMapper.readTree(expected));
        } catch (JsonProcessingException e) {
            throw new AssertionError("Failed to parse JSON for comparison", e);
        }
    }

    @Test
    void previousValueAndNewValueCanEachBeNullIndependently() {
        UUID entityId = UUID.randomUUID();

        AuditLog created = auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Ana Souza", "ana@farelo.dev",
                "PRODUCT_CREATED", "Product", entityId,
                null, "{\"name\":\"Espresso\"}"));

        AuditLog deleted = auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Ana Souza", "ana@farelo.dev",
                "PRODUCT_DELETED", "Product", entityId,
                "{\"name\":\"Espresso\"}", null));

        assertThat(auditLogRepository.findById(created.getId()).orElseThrow().getPreviousValue()).isNull();
        assertThat(auditLogRepository.findById(created.getId()).orElseThrow().getNewValue()).isNotNull();
        assertThat(auditLogRepository.findById(deleted.getId()).orElseThrow().getPreviousValue()).isNotNull();
        assertThat(auditLogRepository.findById(deleted.getId()).orElseThrow().getNewValue()).isNull();
    }

    @Test
    void findsByEntityTypeAndEntityIdOrderedByCreatedAtDesc() throws InterruptedException {
        UUID entityId = UUID.randomUUID();
        UUID otherEntityId = UUID.randomUUID();

        AuditLog first = auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", entityId, "{\"price\":10}", "{\"price\":11}"));
        // Distinct, increasing createdAt for a deterministic order
        // assertion — same pattern already used by
        // NotificationRepositoryIntegrationTests' ordering test.
        Thread.sleep(10);
        AuditLog second = auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", entityId, "{\"price\":11}", "{\"price\":12}"));
        // Same entityId value, but a different entityType — must not be
        // returned by a query scoped to ("Product", entityId).
        auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Ana Souza", "ana@farelo.dev",
                "STOCK_ADJUSTED", "Ingredient", entityId, null, "{\"qty\":5}"));
        // Different entityId entirely.
        auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", otherEntityId, "{\"price\":5}", "{\"price\":6}"));

        List<AuditLog> result =
                auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("Product", entityId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(second.getId());
        assertThat(result.get(1).getId()).isEqualTo(first.getId());
    }

    @Test
    void findsByUserIdOrderedByCreatedAtDesc() throws InterruptedException {
        UUID userId = UUID.randomUUID();

        AuditLog first = auditLogRepository.saveAndFlush(new AuditLog(
                userId, "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", UUID.randomUUID(), "{\"price\":10}", "{\"price\":11}"));
        Thread.sleep(10);
        AuditLog second = auditLogRepository.saveAndFlush(new AuditLog(
                userId, "Ana Souza", "ana@farelo.dev",
                "STOCK_ADJUSTED", "Ingredient", UUID.randomUUID(), null, "{\"qty\":5}"));
        // A different user entirely — must not be returned.
        auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Carlos Lima", "carlos@farelo.dev",
                "PRICE_CHANGED", "Product", UUID.randomUUID(), "{\"price\":5}", "{\"price\":6}"));

        List<AuditLog> result = auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(second.getId());
        assertThat(result.get(1).getId()).isEqualTo(first.getId());
    }

    @Test
    void findAllOrderedByCreatedAtDescIncludesEveryRow() throws InterruptedException {
        AuditLog first = auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Ana Souza", "ana@farelo.dev",
                "PRICE_CHANGED", "Product", UUID.randomUUID(), "{\"price\":10}", "{\"price\":11}"));
        Thread.sleep(10);
        AuditLog second = auditLogRepository.saveAndFlush(new AuditLog(
                UUID.randomUUID(), "Carlos Lima", "carlos@farelo.dev",
                "STOCK_ADJUSTED", "Ingredient", UUID.randomUUID(), null, "{\"qty\":5}"));

        List<AuditLog> result = auditLogRepository.findAllByOrderByCreatedAtDesc();

        assertThat(result.stream().map(AuditLog::getId).toList())
                .contains(first.getId(), second.getId());
        assertThat(result.indexOf(second)).isLessThan(result.indexOf(first));
    }

}
