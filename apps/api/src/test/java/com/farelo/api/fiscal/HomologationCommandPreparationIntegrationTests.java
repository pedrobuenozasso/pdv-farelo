package com.farelo.api.fiscal;

import com.farelo.api.AbstractIntegrationTest;
import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryRepository;
import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductRepository;
import com.farelo.api.command.Command;
import com.farelo.api.command.CommandRepository;
import com.farelo.api.command.CommandStatus;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;
import com.farelo.api.ordering.OrderItemRepository;
import com.farelo.api.ordering.OrderRepository;
import com.farelo.api.ordering.OrderStatus;
import com.farelo.api.payment.Payment;
import com.farelo.api.payment.PaymentMethod;
import com.farelo.api.payment.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests the explicit bridge with a real PostgreSQL pending-document lifecycle. */
@SpringBootTest
class HomologationCommandPreparationIntegrationTests extends AbstractIntegrationTest {
    @Autowired private FiscalPendingCommandService pending;
    @Autowired private FiscalDocumentRepository documents;
    @Autowired private CommandRepository commands;
    @Autowired private OrderRepository orders;
    @Autowired private OrderItemRepository items;
    @Autowired private PaymentRepository payments;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;

    @Test
    void retryAfterFailedPreparationReusesPlaceholderAndSuccessfulPreparationCannotRepeat() {
        Command command = new Command(ThreadLocalRandom.current().nextInt(100_000, 900_000));
        command.setStatus(CommandStatus.CLOSED);
        command = commands.saveAndFlush(command);
        Category category = categories.saveAndFlush(new Category("Fiscal bridge " + UUID.randomUUID()));
        Product product = products.saveAndFlush(new Product("Test sale", new BigDecimal("12.00"), category));
        Order order = new Order(command);
        order.setStatus(OrderStatus.CONFIRMED);
        order = orders.saveAndFlush(order);
        OrderItem item = items.saveAndFlush(new OrderItem(order, product, 1, new BigDecimal("12.00")));
        Payment payment = payments.saveAndFlush(new Payment(command,
                new BigDecimal("12.00"), PaymentMethod.CASH));
        try {
            var preparer = mock(HomologationNfcePreparationService.class);
            var bridge = new HomologationCommandPreparation(pending, preparer);
            var issuer = new NfceXmlBuilder.Issuer("11222333000181", "Empresa Exemplo LTDA",
                    "123456789012", "1", new NfceXmlBuilder.Address(
                    "Rua Exemplo", "100", "Centro", "3550308", "Sao Paulo", "01001000"));
            var input = new HomologationCommandPreparation.Input(command.getNumber(), issuer,
                    "VENDA", 1, "12345678", OffsetDateTime.parse("2026-10-18T12:00:00-03:00"));
            PrivateKey key = mock(PrivateKey.class);
            X509Certificate certificate = mock(X509Certificate.class);
            var calls = new AtomicInteger();
            when(preparer.prepare(any(), same(key), same(certificate))).thenAnswer(invocation -> {
                var preparedInput = (HomologationNfcePreparationService.Input) invocation.getArgument(0);
                if (calls.incrementAndGet() == 1) throw new IllegalArgumentException("invalid XSD");
                FiscalDocument document = documents.findById(preparedInput.documentId()).orElseThrow();
                document.setDocumentNumber(1);
                document.setStatus(FiscalDocumentStatus.PROCESSING);
                documents.saveAndFlush(document);
                return new FiscalAuthorizationRequest(document.getId(), "1".repeat(44), "<signedXml/>");
            });

            assertThatThrownBy(() -> bridge.prepare(input, key, certificate))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid XSD");
            var placeholder = documents.findByCommandOrderByCreatedAtAsc(command);
            assertThat(placeholder).hasSize(1);
            assertThat(placeholder.get(0).getDocumentNumber()).isNull();

            FiscalAuthorizationRequest request = bridge.prepare(input, key, certificate);
            assertThat(request.documentId()).isEqualTo(placeholder.get(0).getId());
            assertThat(documents.findByCommandOrderByCreatedAtAsc(command)).hasSize(1);
            assertThatThrownBy(() -> bridge.prepare(input, key, certificate))
                    .isInstanceOf(FiscalDocumentStartException.class)
                    .hasMessageContaining("already has a fiscal document");
            assertThat(calls.get()).isEqualTo(2);
            var captured = ArgumentCaptor.forClass(HomologationNfcePreparationService.Input.class);
            verify(preparer, times(2)).prepare(captured.capture(), same(key), same(certificate));
            assertThat(captured.getAllValues()).extracting(HomologationNfcePreparationService.Input::documentId)
                    .containsExactly(request.documentId(), request.documentId());
        } finally {
            documents.deleteAll(documents.findByCommandOrderByCreatedAtAsc(command));
            documents.flush();
            payments.delete(payment);
            items.delete(item);
            orders.delete(order);
            products.delete(product);
            categories.delete(category);
            commands.delete(command);
        }
    }
}
