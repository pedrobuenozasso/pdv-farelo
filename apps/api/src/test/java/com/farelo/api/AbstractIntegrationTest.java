package com.farelo.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real PostgreSQL instance. Uses the
 * Testcontainers "singleton container" pattern: a single instance is
 * started once and shared by every subclass in the test run, and is
 * reaped automatically by Testcontainers when the JVM exits.
 *
 * <p>Requires Docker to be available locally.
 *
 * <p><strong>{@code OutboxWorker}'s real {@code @Scheduled} trigger is
 * disabled for every test context by default</strong> (see {@code
 * outbox.worker.poll-interval-ms} below). Spring Test caches {@code
 * ApplicationContext}s across test classes for the whole suite run, so a
 * context created early (e.g. by some other {@code @SpringBootTest} class
 * with no property overrides) keeps its {@code OutboxWorker} bean — and
 * its background scheduled thread — alive for as long as the test JVM
 * lives, polling the same shared singleton Postgres container every test
 * class after it uses. Without this, that background worker can race any
 * test that seeds {@code outbox_event} rows and expects to control
 * exactly when/how they get processed (two real, distinct test failures
 * this caused: {@code OutboxWorkerBatchSizeIntegrationTests}'s exact-count
 * assertions, and {@code OutboxWorkerPrintJobIntegrationTests}'s
 * dispatch-failure assertion — both only reproduced when the *full* suite
 * ran, never in isolation). No test in this suite relies on the trigger
 * firing on its own — every test calls {@code processPendingEvents()}
 * directly, the standard way to test a {@code @Scheduled} method without
 * depending on wall-clock timing — so disabling it here has no coverage
 * cost.
 *
 * <p>Note this can't currently be overridden back down per test class:
 * Spring Test gives {@code @DynamicPropertySource} values higher
 * precedence than {@code @TestPropertySource} ones (the reverse of what
 * you might expect), so a subclass's own {@code @TestPropertySource} for
 * this same key would silently lose to this one. No test needs that today
 * — if one ever does, it'll need a different mechanism (e.g. a
 * constructor/field flag this method reads), not a {@code
 * @TestPropertySource} override.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void overridePostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // See class javadoc — an hour is effectively "never" for a test
        // run, without literally disabling @EnableScheduling (which would
        // need a conditional wrapper around it in FareloApiApplication
        // just for this, more machinery than a property override needs).
        registry.add("outbox.worker.poll-interval-ms", () -> "3600000");
    }

}
