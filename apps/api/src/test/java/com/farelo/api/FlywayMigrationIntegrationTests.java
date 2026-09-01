package com.farelo.api;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Flyway applies the infrastructure migration(s) successfully
 * against a real PostgreSQL instance (via Testcontainers).
 */
@SpringBootTest
class FlywayMigrationIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void appliesInitMigrationSuccessfully() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied)
                .extracting(info -> info.getVersion().toString())
                .contains("1");
        assertThat(flyway.info().current().getState().isApplied()).isTrue();
    }

}
