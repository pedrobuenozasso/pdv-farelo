package com.farelo.api.printing;

import com.farelo.api.catalog.ProductionStation;
import com.farelo.api.ordering.Order;
import com.farelo.api.ordering.OrderItem;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The shape serialized into {@link PrintJob#getContent()} — see that
 * class's javadoc ("Design decision 2") for why {@code content} is a
 * frozen snapshot rather than a live reference back to {@link Order}.
 * Built by {@link PrintJobService#createForOrder(java.util.UUID)} from the
 * order/items already fetched from the database at job-creation time —
 * command number for the ticket header, and each item's product name (not
 * id — a printed ticket needs to be human readable) and quantity.
 *
 * <p>Same shape already exercised by {@code
 * PrintJobRepositoryIntegrationTests}'s {@code SAMPLE_CONTENT} fixture,
 * written in FARELO-071 before this class existed — kept identical here
 * rather than inventing a different one now that something actually
 * builds it.
 *
 * <h2>{@code productionStation} (FARELO-074)</h2>
 *
 * One {@link PrintJob} — and so one {@code PrintJobContent} — now exists
 * per {@link ProductionStation} present in an order (see {@link
 * PrintJobService#createForOrder(java.util.UUID)} for the grouping logic),
 * not one per order. {@code productionStation} names which station this
 * particular content belongs to, so a future reader (the Edge Agent,
 * FARELO-076+) knows which ticket is which <em>without inferring it from
 * the items</em> — inferring would put order/product knowledge back into
 * infrastructure that the prompt mestre (seção 11) says should stay dumb.
 *
 * <p><b>Nullable, mirroring {@link
 * com.farelo.api.catalog.Product#getProductionStation()}</b>: an item whose
 * product has no station assigned (still legal — see that field's javadoc)
 * cannot simply be dropped from printing, so it is grouped into its own
 * {@code PrintJob} with {@code productionStation == null}. Jackson
 * serializes a null field as an explicit {@code "productionStation":null}
 * (no custom {@code ObjectMapper} configuration suppresses nulls anywhere
 * in this project), so the absence of a station is a visible, explicit
 * fact in the JSON — not a silently missing key a future reader could
 * mistake for "field didn't exist yet" — for whoever formats the physical
 * ticket to flag clearly (e.g. print a "SEM ESTAÇÃO — avisar verbalmente"
 * header) instead of the order silently missing part of its ticket. That
 * physical formatting itself is out of scope here (Edge Agent territory,
 * FARELO-076+); this class only guarantees the signal is present and
 * unambiguous in the data it hands off.
 *
 * <h2>{@code customerName}/{@code customerPhone}/{@code orderCreatedAt}</h2>
 *
 * Same frozen-snapshot posture as everything else here: copied straight off
 * {@link Order} (already in hand at {@link #from} — no extra query) at
 * ticket-build time, not a live reference. {@code customerName}/{@code
 * customerPhone} are nullable, mirroring {@link Order#getCustomerName()}/
 * {@link Order#getCustomerPhone()} (a walk-in comanda started without going
 * through the customer-facing menu flow has neither) — printed as an
 * explicit "not informed" placeholder by whoever formats the ticket (Edge
 * Agent territory), never a missing key.
 */
public record PrintJobContent(
        Integer commandNumber,
        ProductionStation productionStation,
        List<Item> items,
        String customerName,
        String customerPhone,
        OffsetDateTime orderCreatedAt) {

    public record Item(String productName, int quantity) {
    }

    // FARELO-312: commandNumber is nullable — null for a BALCAO order (see
    // Order's javadoc, "Design decision — BALCAO"), which has no Command
    // to print a number for. Whoever formats the physical ticket (Edge
    // Agent territory) is expected to print a "BALCÃO" header instead of a
    // comanda number in that case, the same "explicit signal, not a
    // silently missing key" posture this record's own javadoc already
    // documents for a null productionStation.
    public static PrintJobContent from(Order order, ProductionStation productionStation, List<OrderItem> items) {
        List<Item> printItems = items.stream()
                .map(item -> new Item(item.getProduct().getName(), item.getQuantity()))
                .toList();

        Integer commandNumber = order.getCommand() != null ? order.getCommand().getNumber() : null;

        return new PrintJobContent(
                commandNumber,
                productionStation,
                printItems,
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getCreatedAt());
    }

}
