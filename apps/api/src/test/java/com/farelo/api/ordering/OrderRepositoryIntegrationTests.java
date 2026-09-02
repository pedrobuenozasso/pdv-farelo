package com.farelo.api.ordering;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

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

}
