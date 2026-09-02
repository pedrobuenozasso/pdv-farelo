package com.farelo.api.ordering;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link Order} maps correctly onto the {@code orders} table
 * created by {@code V7__create_order_table.sql}, including its FK to
 * {@link Command}, against a real PostgreSQL instance.
 */
@SpringBootTest
class OrderRepositoryIntegrationTests extends AbstractIntegrationTest {

    // Command #8 from the FARELO-031 seed — not touched by any other test
    // in the command domain (see CommandControllerIntegrationTests'
    // javadoc for the numbers already spoken for: 1-7, 50, 100, 101, 999).
    private static final int SEEDED_COMMAND_NUMBER = 8;

    // Command #16 — dedicated to the FARELO-059 queue-query test below,
    // distinct from every number already spoken for elsewhere (see
    // CommandOrdersControllerIntegrationTests' javadoc: 1-7, 8, 9, 10-13,
    // 14, 15, 101, 999).
    private static final int QUEUE_TEST_COMMAND_NUMBER = 16;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void savesAndFindsOrderLinkedToCommand() {
        Command command = commandRepository.findByNumber(SEEDED_COMMAND_NUMBER).orElseThrow();

        Order order = orderRepository.saveAndFlush(new Order(command));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();

        Optional<Order> found = orderRepository.findById(order.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getCommand().getId()).isEqualTo(command.getId());
        assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    // FARELO-059: findByStatusInOrderByCreatedAtAsc backs the kitchen
    // queue (OrderService#listQueue). Postgres is a singleton container
    // shared by every test class in the run (see AbstractIntegrationTest),
    // so other classes' orders may already sit in the table when this
    // runs — assertions below check relative order/(non-)membership among
    // *this test's own* orders rather than an absolute result size.
    @Test
    void findsOrdersInQueueStatusesOrderedByCreatedAtAscExcludingReadyAndTerminalStatuses() throws InterruptedException {
        Command command = commandRepository.findByNumber(QUEUE_TEST_COMMAND_NUMBER).orElseThrow();

        UUID createdId = orderRepository.saveAndFlush(new Order(command)).getId();

        // Sleeps guarantee distinct, increasing createdAt timestamps, so
        // the FIFO ordering assertion below is deterministic rather than
        // relying on clock resolution alone (same pattern as
        // CommandOrdersControllerIntegrationTests).
        Thread.sleep(10);
        Order confirmedOrder = new Order(command);
        confirmedOrder.setStatus(OrderStatus.CONFIRMED);
        UUID confirmedId = orderRepository.saveAndFlush(confirmedOrder).getId();

        Thread.sleep(10);
        Order preparingOrder = new Order(command);
        preparingOrder.setStatus(OrderStatus.PREPARING);
        UUID preparingId = orderRepository.saveAndFlush(preparingOrder).getId();

        Order readyOrder = new Order(command);
        readyOrder.setStatus(OrderStatus.READY);
        UUID readyId = orderRepository.saveAndFlush(readyOrder).getId();

        Order cancelledOrder = new Order(command);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        UUID cancelledId = orderRepository.saveAndFlush(cancelledOrder).getId();

        List<Order> queue = orderRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(OrderStatus.CREATED, OrderStatus.CONFIRMED, OrderStatus.PREPARING));
        List<UUID> queueIds = queue.stream().map(Order::getId).toList();

        assertThat(queueIds).doesNotContain(readyId, cancelledId);

        List<UUID> ourQueueIds = queueIds.stream()
                .filter(id -> id.equals(createdId) || id.equals(confirmedId) || id.equals(preparingId))
                .toList();
        assertThat(ourQueueIds).containsExactly(createdId, confirmedId, preparingId);

        // JOIN FETCH: command is already initialized, no
        // LazyInitializationException reading it after this method returns.
        Order first = queue.stream().filter(o -> o.getId().equals(createdId)).findFirst().orElseThrow();
        assertThat(first.getCommand().getNumber()).isEqualTo(QUEUE_TEST_COMMAND_NUMBER);
    }

}
