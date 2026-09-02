package com.farelo.api.ordering;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link OrderItem} maps correctly onto the table created by
 * {@code V8__create_order_item_table.sql}, including its FKs to
 * {@link Order} and {@link Product}, against a real PostgreSQL instance.
 */
@SpringBootTest
class OrderItemRepositoryIntegrationTests extends AbstractIntegrationTest {

    // Command #9 from the FARELO-031 seed — distinct from #8 (used by
    // OrderRepositoryIntegrationTests) and from the numbers already spoken
    // for by the command domain's own tests (1-7, 50, 100, 101, 999).
    private static final int SEEDED_COMMAND_NUMBER = 9;

    @Autowired
    private CommandRepository commandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Test
    void savesAndFindsOrderItemLinkedToOrderAndProduct() {
        Command command = commandRepository.findByNumber(SEEDED_COMMAND_NUMBER).orElseThrow();
        Order order = orderRepository.saveAndFlush(new Order(command));

        Category category = categoryRepository.save(new Category("Bebidas"));
        Product product = productRepository.save(new Product("Café Espresso", new BigDecimal("7.50"), category));

        OrderItem orderItem = orderItemRepository.saveAndFlush(
                new OrderItem(order, product, 2, new BigDecimal("7.50")));

        assertThat(orderItem.getId()).isNotNull();
        assertThat(orderItem.getCreatedAt()).isNotNull();

        Optional<OrderItem> found = orderItemRepository.findById(orderItem.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOrder().getId()).isEqualTo(order.getId());
        assertThat(found.get().getProduct().getId()).isEqualTo(product.getId());
        assertThat(found.get().getQuantity()).isEqualTo(2);
        // BigDecimal price: compare by value, not by scale (AGENTS.md: money
        // is always BigDecimal) — same reasoning as other price assertions
        // (e.g. ProductRepositoryIntegrationTests).
        assertThat(found.get().getUnitPrice()).isEqualByComparingTo(new BigDecimal("7.50"));
    }

}
