package com.farelo.api.printing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link PrintJob} maps correctly onto the table created by
 * {@code V14__create_print_job_table.sql}, including its FK to {@link
 * Order}, against a real PostgreSQL instance.
 */
@SpringBootTest
class PrintJobRepositoryIntegrationTests extends AbstractIntegrationTest {

    // Command #17 from the FARELO-031 seed — distinct from every command
    // number already spoken for by other repository tests (see e.g. the
    // comment in OrderItemRepositoryIntegrationTests: 8/9/10/11/14/16 are
    // already taken; 1-5 by the command domain's own tests).
    private static final int SEEDED_COMMAND_NUMBER = 17;

    private static final String SAMPLE_CONTENT =
            "{\"commandNumber\":17,\"items\":[{\"productName\":\"Café Espresso\",\"quantity\":2}]}";

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PrintJobRepository printJobRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void savesAndFindsPrintJobLinkedToOrder() {
        Command command = commandRepository.findByNumber(SEEDED_COMMAND_NUMBER).orElseThrow();
        Order order = orderRepository.saveAndFlush(new Order(command));

        PrintJob printJob = printJobRepository.saveAndFlush(new PrintJob(order, SAMPLE_CONTENT));

        assertThat(printJob.getId()).isNotNull();
        assertThat(printJob.getStatus()).isEqualTo(PrintJobStatus.PENDING);
        assertThat(printJob.getCreatedAt()).isNotNull();
        assertThat(printJob.getUpdatedAt()).isNotNull();

        Optional<PrintJob> found = printJobRepository.findById(printJob.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOrder().getId()).isEqualTo(order.getId());
        // Compare as parsed JSON, not raw string: Postgres' jsonb column
        // normalizes key order/whitespace on round-trip (it is not a text
        // column), so a byte-for-byte comparison against what was written
        // would be comparing the wrong thing.
        assertThatJson(found.get().getContent(), SAMPLE_CONTENT);
        assertThat(found.get().getStatus()).isEqualTo(PrintJobStatus.PENDING);
    }

    @Test
    void markPrintedAndMarkFailedTransitionStatus() {
        Command command = commandRepository.findByNumber(SEEDED_COMMAND_NUMBER).orElseThrow();
        Order order = orderRepository.saveAndFlush(new Order(command));

        PrintJob printedJob = printJobRepository.saveAndFlush(new PrintJob(order, SAMPLE_CONTENT));
        printedJob.markPrinted();
        printJobRepository.saveAndFlush(printedJob);

        assertThat(printJobRepository.findById(printedJob.getId()).orElseThrow().getStatus())
                .isEqualTo(PrintJobStatus.PRINTED);

        PrintJob failedJob = printJobRepository.saveAndFlush(new PrintJob(order, SAMPLE_CONTENT));
        failedJob.markFailed();
        printJobRepository.saveAndFlush(failedJob);

        assertThat(printJobRepository.findById(failedJob.getId()).orElseThrow().getStatus())
                .isEqualTo(PrintJobStatus.FAILED);
    }

    /**
     * Compares two JSON strings by parsed value, not raw text — see the
     * comment at the call site for why a plain string comparison doesn't
     * work against a value read back from a {@code jsonb} column.
     */
    private void assertThatJson(String actual, String expected) {
        try {
            assertThat(objectMapper.readTree(actual)).isEqualTo(objectMapper.readTree(expected));
        } catch (JsonProcessingException e) {
            throw new AssertionError("Failed to parse JSON for comparison", e);
        }
    }

}
