package com.farelo.api.printing;

import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;

import java.util.List;

/**
 * The shape serialized into {@link PrintJob#getContent()} — see that
 * class's javadoc ("Design decision 2") for why {@code content} is a
 * frozen snapshot rather than a live reference back to {@link Order}.
 * Built by {@link PrintJobService#createForOrder(java.util.UUID)}
 * (FARELO-072) from the order/items already fetched from the database at
 * job-creation time — command number for the ticket header, and each
 * item's product name (not id — a printed ticket needs to be human
 * readable) and quantity.
 *
 * <p>Same shape already exercised by {@code
 * PrintJobRepositoryIntegrationTests}'s {@code SAMPLE_CONTENT} fixture,
 * written in FARELO-071 before this class existed — kept identical here
 * rather than inventing a different one now that something actually
 * builds it.
 */
public record PrintJobContent(int commandNumber, List<Item> items) {

    public record Item(String productName, int quantity) {
    }

    public static PrintJobContent from(Order order, List<OrderItem> items) {
        List<Item> printItems = items.stream()
                .map(item -> new Item(item.getProduct().getName(), item.getQuantity()))
                .toList();

        return new PrintJobContent(order.getCommand().getNumber(), printItems);
    }

}
