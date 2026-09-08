package com.farelo.api.table.web;

/**
 * Response body for {@code GET /api/v1/tables/occupancy} (FARELO-315) —
 * the three occupancy indicators named in {@code docs/gaps-sischef-pdv.md}
 * item 3 ("Mesas ocupadas", "Ocupação %"), <b>minus</b> "Qtde de Pessoas".
 *
 * <p><b>"Qtde de Pessoas" (headcount) is deliberately not implemented
 * here</b>: it would require tracking an actual party size somewhere
 * (how many people are sitting at a table right now), and neither {@link
 * com.farelo.api.table.DiningTable} (this ticket's literal field list is
 * "número, capacidade" — {@code capacity} is a table's maximum seating,
 * not who's actually there today) nor {@link com.farelo.api.command.Command}
 * carries that data. Adding it would mean introducing a new field
 * captured somewhere in the order/comanda-opening flow — squarely the
 * "não tocar em OrderController/OrderService/telas de PDV" boundary this
 * ticket's scope stops at. Left as a known gap for a future ticket to
 * design (e.g. a {@code partySize} captured when a comanda opens).
 *
 * <p>{@code occupiedTables} counts distinct tables currently linked to a
 * {@code Command} in an "in active use" status (see {@code
 * CommandService#countOccupiedTables}'s javadoc for exactly which). {@code
 * occupancyPercentage} is {@code occupiedTables / totalTables * 100},
 * rounded to one decimal place, or {@code 0} when there are no tables at
 * all (avoids a division by zero on an empty floor plan).
 */
public record TableOccupancyResponse(long occupiedTables, long totalTables, double occupancyPercentage) {

    public static TableOccupancyResponse of(long occupiedTables, long totalTables) {
        double percentage = totalTables == 0
                ? 0.0
                : Math.round(occupiedTables * 1000.0 / totalTables) / 10.0;
        return new TableOccupancyResponse(occupiedTables, totalTables, percentage);
    }

}
