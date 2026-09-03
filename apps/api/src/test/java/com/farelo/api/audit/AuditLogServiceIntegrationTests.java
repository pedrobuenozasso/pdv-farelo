package com.farelo.api.audit;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link AuditLogService}'s two methods against a real PostgreSQL
 * instance: {@link AuditLogService#record} correctly snapshots the given
 * {@link User}'s id/name/email onto a new {@link AuditLog}, and {@link
 * AuditLogService#list}'s filter precedence (userId first, then
 * entityType+entityId together, otherwise everything — see that method's
 * javadoc).
 *
 * <p>No {@code @BeforeEach} table cleanup, same reasoning as {@code
 * AuditLogRepositoryIntegrationTests}: every test scopes its assertions to
 * its own randomly-generated ids.
 */
@SpringBootTest
class AuditLogServiceIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void recordSnapshotsActorIdNameAndEmailOntoNewAuditLog() {
        User actor = userRepository.saveAndFlush(
                new User("Ana Souza", "ana+" + UUID.randomUUID() + "@farelo.dev", "bcrypt-hash", UserRole.ADMIN));
        UUID entityId = UUID.randomUUID();

        AuditLog recorded = auditLogService.record(
                actor, "PRICE_CHANGED", "Product", entityId, "{\"price\":10}", "{\"price\":12}");

        assertThat(recorded.getId()).isNotNull();
        assertThat(recorded.getUserId()).isEqualTo(actor.getId());
        assertThat(recorded.getUserName()).isEqualTo("Ana Souza");
        assertThat(recorded.getUserEmail()).isEqualTo(actor.getEmail());
        assertThat(recorded.getAction()).isEqualTo("PRICE_CHANGED");
        assertThat(recorded.getEntityType()).isEqualTo("Product");
        assertThat(recorded.getEntityId()).isEqualTo(entityId);
        assertThat(recorded.getCreatedAt()).isNotNull();

        // Actually persisted, not just returned in memory.
        assertThat(auditLogRepository.findById(recorded.getId())).isPresent();
    }

    @Test
    void recordKeepsTheActorsSnapshotEvenIfTheUserRowLaterChanges() {
        User actor = userRepository.saveAndFlush(
                new User("Bruno Alves", "bruno+" + UUID.randomUUID() + "@farelo.dev", "bcrypt-hash", UserRole.MANAGER));

        AuditLog recorded = auditLogService.record(
                actor, "STOCK_ADJUSTED", "Ingredient", UUID.randomUUID(), null, "{\"qty\":5}");

        actor.setName("Bruno Alves Renamed");
        actor.setEmail("bruno.renamed+" + UUID.randomUUID() + "@farelo.dev");
        userRepository.saveAndFlush(actor);

        AuditLog reloaded = auditLogRepository.findById(recorded.getId()).orElseThrow();
        assertThat(reloaded.getUserName()).isEqualTo("Bruno Alves");
        assertThat(reloaded.getUserEmail()).isNotEqualTo(actor.getEmail());
    }

    @Test
    void listWithNoFilterReturnsEveryRowNewestFirst() throws InterruptedException {
        User actor = userRepository.saveAndFlush(
                new User("Carla Dias", "carla+" + UUID.randomUUID() + "@farelo.dev", "bcrypt-hash", UserRole.ADMIN));

        AuditLog older = auditLogService.record(
                actor, "PRICE_CHANGED", "Product", UUID.randomUUID(), "{\"price\":1}", "{\"price\":2}");
        Thread.sleep(10);
        AuditLog newer = auditLogService.record(
                actor, "STOCK_ADJUSTED", "Ingredient", UUID.randomUUID(), null, "{\"qty\":3}");

        List<AuditLog> result = auditLogService.list(null, null, null);

        assertThat(result.stream().map(AuditLog::getId).toList())
                .contains(older.getId(), newer.getId());
        assertThat(result.indexOf(newer)).isLessThan(result.indexOf(older));
    }

    @Test
    void listFiltersByEntityTypeAndEntityIdOnlyWhenBothAreGiven() {
        User actor = userRepository.saveAndFlush(
                new User("Diego Reis", "diego+" + UUID.randomUUID() + "@farelo.dev", "bcrypt-hash", UserRole.ADMIN));
        UUID entityId = UUID.randomUUID();

        AuditLog match = auditLogService.record(
                actor, "PRICE_CHANGED", "Product", entityId, "{\"price\":1}", "{\"price\":2}");
        AuditLog otherEntity = auditLogService.record(
                actor, "PRICE_CHANGED", "Product", UUID.randomUUID(), "{\"price\":1}", "{\"price\":2}");

        List<AuditLog> filtered = auditLogService.list("Product", entityId, null);
        assertThat(filtered.stream().map(AuditLog::getId).toList())
                .contains(match.getId())
                .doesNotContain(otherEntity.getId());

        // entityId given without entityType is treated as no filter at all
        // (see AuditLogService#list's javadoc) — both rows above must be
        // present, not just silently ignored/erroring.
        List<AuditLog> partial = auditLogService.list(null, entityId, null);
        assertThat(partial.stream().map(AuditLog::getId).toList())
                .contains(match.getId(), otherEntity.getId());
    }

    @Test
    void listFiltersByUserIdAndTakesPriorityOverEntityFilter() {
        User first = userRepository.saveAndFlush(
                new User("Elis Ramos", "elis+" + UUID.randomUUID() + "@farelo.dev", "bcrypt-hash", UserRole.ADMIN));
        User second = userRepository.saveAndFlush(
                new User("Fabio Nunes", "fabio+" + UUID.randomUUID() + "@farelo.dev", "bcrypt-hash", UserRole.ADMIN));
        UUID entityId = UUID.randomUUID();

        AuditLog byFirst = auditLogService.record(
                first, "PRICE_CHANGED", "Product", entityId, "{\"price\":1}", "{\"price\":2}");
        AuditLog bySecond = auditLogService.record(
                second, "PRICE_CHANGED", "Product", entityId, "{\"price\":2}", "{\"price\":3}");

        List<AuditLog> result = auditLogService.list("Product", entityId, first.getId());

        assertThat(result.stream().map(AuditLog::getId).toList())
                .contains(byFirst.getId())
                .doesNotContain(bySecond.getId());
    }

}
