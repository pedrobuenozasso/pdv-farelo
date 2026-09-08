package com.farelo.api.table.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic unit test for {@link TableOccupancyResponse#of}'s percentage
 * rounding/division-by-zero handling — no Spring context, no database,
 * same split every other service/DTO in this codebase uses between logic
 * tests and full-stack integration tests (the actual aggregate counts
 * against real {@code Command}/{@code DiningTable} rows are covered by
 * {@code DiningTableControllerIntegrationTests}).
 */
class TableOccupancyResponseTests {

    @Test
    void computesRoundedPercentage() {
        TableOccupancyResponse response = TableOccupancyResponse.of(1, 3);

        assertThat(response.occupiedTables()).isEqualTo(1);
        assertThat(response.totalTables()).isEqualTo(3);
        assertThat(response.occupancyPercentage()).isEqualTo(33.3);
    }

    @Test
    void isFullyOccupiedWhenEveryTableIsOccupied() {
        assertThat(TableOccupancyResponse.of(5, 5).occupancyPercentage()).isEqualTo(100.0);
    }

    @Test
    void avoidsDivisionByZeroWhenThereAreNoTables() {
        TableOccupancyResponse response = TableOccupancyResponse.of(0, 0);

        assertThat(response.occupancyPercentage()).isEqualTo(0.0);
    }

}
