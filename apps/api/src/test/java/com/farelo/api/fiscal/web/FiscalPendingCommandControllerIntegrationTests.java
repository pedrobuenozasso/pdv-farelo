package com.farelo.api.fiscal.web;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.fiscal.FiscalDocumentRepository;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
import com.farelo.api.security.User;
import com.farelo.api.security.UserRepository;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FiscalPendingCommandControllerIntegrationTests extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CommandRepository commands;
    @Autowired FiscalDocumentRepository documents;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired PaymentRepository payments;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenService jwt;

    private final List<Command> createdCommands = new ArrayList<>();
    private final List<Order> createdOrders = new ArrayList<>();
    private final List<OrderItem> createdItems = new ArrayList<>();
    private final List<Payment> createdPayments = new ArrayList<>();
    private final List<Category> createdCategories = new ArrayList<>();
    private final List<Product> createdProducts = new ArrayList<>();
    private final List<User> createdUsers = new ArrayList<>();

    private Command command(CommandStatus status) {
        var value = new Command(ThreadLocalRandom.current().nextInt(100_000, 900_000));
        value.setStatus(status);
        value = commands.saveAndFlush(value);
        createdCommands.add(value);
        return value;
    }

    private void sale(Command command, OrderStatus status, String payment) {
        Category category = categories.saveAndFlush(new Category("Fiscal test " + UUID.randomUUID()));
        createdCategories.add(category);
        Product product = products.saveAndFlush(new Product("Test sale", new BigDecimal("12.00"), category));
        createdProducts.add(product);
        Order order = new Order(command);
        order.setStatus(status);
        order = orders.saveAndFlush(order);
        createdOrders.add(order);
        createdItems.add(items.saveAndFlush(new OrderItem(order, product, 1, new BigDecimal("12.00"))));
        if (payment != null) {
            createdPayments.add(payments.saveAndFlush(new Payment(command,
                    new BigDecimal(payment), PaymentMethod.CASH)));
        }
    }

    private String token(UserRole role) {
        User user = users.saveAndFlush(new User("Fiscal test", "fiscal-" + UUID.randomUUID() + "@farelo.dev",
                passwordEncoder.encode("test-password"), role));
        createdUsers.add(user);
        return jwt.issue(user).token();
    }

    private String path(Command command) {
        return "/api/v1/commands/" + command.getNumber() + "/fiscal-documents/pending";
    }

    @AfterEach
    void cleanup() {
        for (Command command : createdCommands) {
            documents.deleteAll(documents.findByCommandOrderByCreatedAtAsc(command));
        }
        documents.flush();
        payments.deleteAll(createdPayments);
        items.deleteAll(createdItems);
        orders.deleteAll(createdOrders);
        products.deleteAll(createdProducts);
        categories.deleteAll(createdCategories);
        commands.deleteAll(createdCommands);
        users.deleteAll(createdUsers);
    }

    @Test
    void createsAndReusesOnlyOnePendingDocumentWithoutFiscalMaterial() throws Exception {
        Command command = command(CommandStatus.CLOSED);
        sale(command, OrderStatus.CONFIRMED, "12.00");
        String bearer = "Bearer " + token(UserRole.ADMIN);
        String first = mvc.perform(post(path(command)).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.commandNumber").value(command.getNumber()))
                .andExpect(jsonPath("$.xmlContent").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post(path(command)).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first);
        assertThat(documents.findByCommandOrderByCreatedAtAsc(command)).hasSize(1);
        assertThat(documents.findByCommandOrderByCreatedAtAsc(command).get(0).getDocumentNumber()).isNull();
    }

    @Test
    void rejectsUnauthorizedAndNonAdministrativeRolesWithoutWriting() throws Exception {
        Command command = command(CommandStatus.CLOSED);
        sale(command, OrderStatus.CONFIRMED, "12.00");
        mvc.perform(post(path(command)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path(command)).header("Authorization", "Bearer " + token(UserRole.CASHIER)))
                .andExpect(status().isForbidden());
        assertThat(documents.findByCommandOrderByCreatedAtAsc(command)).isEmpty();
    }

    @Test
    void requiresClosedConfirmedAndPaidPositiveSale() throws Exception {
        String bearer = "Bearer " + token(UserRole.MANAGER);
        Command open = command(CommandStatus.OPEN);
        sale(open, OrderStatus.CONFIRMED, "12.00");
        Command empty = command(CommandStatus.CLOSED);
        Command created = command(CommandStatus.CLOSED);
        sale(created, OrderStatus.CREATED, "12.00");
        Command unpaid = command(CommandStatus.CLOSED);
        sale(unpaid, OrderStatus.CONFIRMED, "5.00");
        for (Command command : List.of(open, empty, created, unpaid)) {
            mvc.perform(post(path(command)).header("Authorization", bearer))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FISCAL_DOCUMENT_NOT_ELIGIBLE"));
            assertThat(documents.findByCommandOrderByCreatedAtAsc(command)).isEmpty();
        }
    }

    @Test
    void refusesImplicitReissueAfterPlaceholderHasBeenUsed() throws Exception {
        Command command = command(CommandStatus.CLOSED);
        sale(command, OrderStatus.CONFIRMED, "12.00");
        var document = documents.saveAndFlush(new com.farelo.api.fiscal.FiscalDocument(command));
        document.setDocumentNumber(123);
        documents.saveAndFlush(document);
        mvc.perform(post(path(command)).header("Authorization", "Bearer " + token(UserRole.ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FISCAL_DOCUMENT_ALREADY_STARTED"));
        assertThat(documents.findByCommandOrderByCreatedAtAsc(command)).hasSize(1);
    }
}
