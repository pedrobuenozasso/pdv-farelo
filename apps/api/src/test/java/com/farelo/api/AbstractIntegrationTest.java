package com.farelo.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.URI;

/**
 * Base class for tests that need a real PostgreSQL instance. By default it
 * uses the Testcontainers "singleton container" pattern: one instance is
 * shared by every subclass and reaped when the JVM exits.
 *
 * <p>Requires Docker locally by default. When the local Docker daemon is
 * unavailable, tests may use an explicitly isolated PostgreSQL database
 * through a loopback-only tunnel by setting all three
 * {@code FARELO_TEST_JDBC_URL}, {@code FARELO_TEST_DB_USER} and
 * {@code FARELO_TEST_DB_PASSWORD}. The database must be named
 * {@code farelo_integration_test}; this guard rejects ordinary production
 * URLs. The external database must still be created solely for tests.
 *
 * <p><strong>{@code OutboxWorker}'s and {@code NotificationWorker}'s real
 * {@code @Scheduled} triggers are disabled for every test context by
 * default</strong> (see {@code outbox.worker.poll-interval-ms}/{@code
 * notification.worker.poll-interval-ms} below). Spring Test caches {@code
 * ApplicationContext}s across test classes for the whole suite run, so a
 * context created early (e.g. by some other {@code @SpringBootTest} class
 * with no property overrides) keeps its worker beans — and their
 * background scheduled threads — alive for as long as the test JVM lives,
 * polling the same shared singleton Postgres container every test class
 * after it uses. Without this, a background worker can race any test that
 * seeds {@code outbox_event}/{@code notification} rows and expects to
 * control exactly when/how they get processed (two real, distinct test
 * failures this caused for {@code OutboxWorker}: {@code
 * OutboxWorkerBatchSizeIntegrationTests}'s exact-count assertions, and
 * {@code OutboxWorkerPrintJobIntegrationTests}'s dispatch-failure assertion
 * — both only reproduced when the *full* suite ran, never in isolation;
 * {@code NotificationWorker}'s poll-interval override, FARELO-112,
 * preempts the same class of failure for {@code
 * NotificationSenderIntegrationTests} and friends, which seed {@code
 * PENDING} notifications directly and would otherwise race a live
 * background sender against a real, unconfigured WhatsApp endpoint). No
 * test in this suite relies on either trigger firing on its own — every
 * test calls {@code processPendingEvents()}/{@code
 * processPendingNotifications()} directly, the standard way to test a
 * {@code @Scheduled} method without depending on wall-clock timing — so
 * disabling both here has no coverage cost.
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

    private static final String EXTERNAL_URL = System.getenv("FARELO_TEST_JDBC_URL");
    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (EXTERNAL_URL == null) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
            POSTGRES.start();
        } else {
            requireDisposableLoopbackDatabase(EXTERNAL_URL);
            requirePresent("FARELO_TEST_DB_USER");
            requirePresent("FARELO_TEST_DB_PASSWORD");
            POSTGRES = null;
        }
    }

    private static void requireDisposableLoopbackDatabase(String jdbcUrl) {
        try {
            if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
                throw new IllegalArgumentException("Not a PostgreSQL JDBC URL");
            }
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String host = uri.getHost();
            String database = uri.getPath();
            if (!("127.0.0.1".equals(host) || "localhost".equals(host))
                    || uri.getPort() <= 0 || uri.getPort() == 5432
                    || !"/farelo_integration_test".equals(database)
                    || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Not an isolated loopback test database");
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException("FARELO_TEST_JDBC_URL must target loopback, a non-default port, "
                    + "and database farelo_integration_test", ex);
        }
    }

    private static String requirePresent(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the external test database");
        }
        return value;
    }

    @DynamicPropertySource
    static void overridePostgresProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES == null) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> requirePresent("FARELO_TEST_DB_USER"));
            registry.add("spring.datasource.password", () -> requirePresent("FARELO_TEST_DB_PASSWORD"));
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
        // See class javadoc — an hour is effectively "never" for a test
        // run, without literally disabling @EnableScheduling (which would
        // need a conditional wrapper around it in FareloApiApplication
        // just for this, more machinery than a property override needs).
        registry.add("outbox.worker.poll-interval-ms", () -> "3600000");
        // FARELO-112: same reasoning, for NotificationWorker.
        registry.add("notification.worker.poll-interval-ms", () -> "3600000");
    }

}
