package com.farelo.api.table.web;

import com.farelo.api.command.CommandService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import com.farelo.api.table.DiningTable;
import com.farelo.api.table.DiningTableService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * {@code /api/v1/tables} (FARELO-315) — {@code POST} (create), {@code GET}
 * (list) and {@code GET .../occupancy} (the three Sischef-parity
 * indicators, minus headcount — see {@link TableOccupancyResponse}'s
 * javadoc for why).
 *
 * <p><b>{@link #create} requires {@link UserRole#ADMIN}/
 * {@link UserRole#MANAGER}</b> — same role list as {@code
 * CategoryController}/{@code ProductController}'s writes, and the same
 * reasoning: laying out the dining room's tables is infrequent back-office
 * setup, not a routine front-of-house action, unlike {@code
 * CustomerController}'s broader role list (see that controller's javadoc
 * for the contrast).
 *
 * <p><b>{@link #list}/{@link #occupancy} require a caller role too</b>,
 * unlike {@code CategoryController#list}/{@code ProductController#list}:
 * there is no anonymous/public consumer of the table map (the "Cardápio
 * QR" flow never needs it), so the default (protect it) applies — same
 * divergence {@code CustomerController}'s javadoc already documents for
 * the same reason. The role list here is the front-of-house one ({@code
 * ADMIN}/{@code MANAGER}/{@code CASHIER}/{@code ATTENDANT}) rather than
 * {@code ADMIN}/{@code MANAGER} alone, though: staff working the floor
 * need to see which tables exist and how occupied the room is, the same
 * "front-of-house operational read" nature as {@code CommandController
 * #findByNumber} being needed by staff even though it stays unprotected
 * for a different (public) reason.
 *
 * <p>{@link #occupancy} depends on {@link CommandService} — a one-directional
 * {@code table.web → command} dependency distinct from this class's own
 * {@code table} core (entity/repository/service), which has no dependency
 * on {@code command} at all. See {@code DiningTableService}'s javadoc and
 * {@code CommandService#countOccupiedTables}'s javadoc for the full
 * reasoning on why the aggregate lives there instead of here.
 */
@RestController
@RequestMapping("/api/v1/tables")
public class DiningTableController {

    private final DiningTableService diningTableService;
    private final CommandService commandService;

    public DiningTableController(DiningTableService diningTableService, CommandService commandService) {
        this.diningTableService = diningTableService;
        this.commandService = commandService;
    }

    @PostMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public ResponseEntity<DiningTableResponse> create(
            @Valid @RequestBody DiningTableRequest request, UriComponentsBuilder uriComponentsBuilder) {
        DiningTable table = diningTableService.create(request.number(), request.capacity());

        URI location = uriComponentsBuilder
                .path("/api/v1/tables/{id}")
                .buildAndExpand(table.getId())
                .toUri();

        return ResponseEntity.created(location).body(DiningTableResponse.from(table));
    }

    @GetMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public List<DiningTableResponse> list() {
        return diningTableService.listAll().stream()
                .map(DiningTableResponse::from)
                .toList();
    }

    @GetMapping("/occupancy")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public TableOccupancyResponse occupancy() {
        long totalTables = diningTableService.count();
        long occupiedTables = commandService.countOccupiedTables();
        return TableOccupancyResponse.of(occupiedTables, totalTables);
    }

}
