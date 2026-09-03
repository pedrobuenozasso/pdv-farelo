package com.farelo.api.command.web;

import com.farelo.api.command.Command;
import com.farelo.api.command.CommandService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/v1/commands} — {@code POST .../open}/{@code .../close}
 * (FARELO-033/034) require a staff role as of FARELO-124; {@link
 * #findByNumber} stays deliberately unprotected — see its own javadoc.
 *
 * <p><b>Why {@link #open}/{@link #close} use different role lists instead
 * of sharing one</b>: both are front-of-house actions — no kitchen role
 * ({@code KITCHEN}) has any business calling either, so it's excluded from
 * both — but they aren't the same kind of operation. {@link #open} just
 * marks a comanda as being used (a customer sat down, staff starts their
 * tab) and carries no money implication yet, so {@code ATTENDANT} (table
 * service) does this routinely, same as {@code CASHIER}. {@link #close},
 * on the other hand, is the step that conceptually settles the tab — even
 * though FARELO-034 doesn't wire up a real payment check yet (that's
 * FARELO-143), this codebase treats "closing" as a cash-handling action,
 * not a general floor-service one, so {@link #close} deliberately does
 * <b>not</b> include {@code ATTENDANT} — narrower than {@link #open} on
 * purpose, not an oversight. {@code ADMIN}/{@code MANAGER} are added to
 * both, same "can always do what any staff role can" reasoning FARELO-123
 * already used for {@code CategoryController}/{@code ProductController}.
 */
@RestController
@RequestMapping("/api/v1/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) {
        this.commandService = commandService;
    }

    // {number} is the human-facing business identifier (1-100), never the
    // technical UUID id — see Command's javadoc / prompt mestre seção 41.
    //
    // FARELO-124: deliberately left WITHOUT @RequireRole. The
    // customer-facing "Cardápio QR" flow (apps/web,
    // app/c/[commandNumber]/page.tsx, a Server Component with no login of
    // any kind — prompt mestre seção 6) calls exactly this endpoint,
    // server-side, to validate the comanda number from the URL before
    // showing the menu. It's also called from the internal /pdv screen,
    // but a single endpoint can't be restricted for one caller and left
    // open for another — same "public dependency, don't lock it down"
    // reasoning FARELO-123 already applied to
    // GET /api/v1/categories/GET /api/v1/products.
    @GetMapping("/{number}")
    public CommandResponse findByNumber(@PathVariable int number) {
        Command command = commandService.findByNumber(number);
        return CommandResponse.from(command);
    }

    // POST, not PATCH: /open is an action ("open this command"), not a
    // partial update of the resource's representation — a pragmatic REST
    // convention for state-transition endpoints (verb suffix + POST), and
    // it sidesteps PATCH's usual implication of a body describing the
    // partial change (e.g. JSON Patch/Merge Patch), which this endpoint
    // doesn't have.
    //
    // Note the QR customer flow never calls this directly: creating the
    // first order on an AVAILABLE comanda already transitions it to OPEN
    // as a side effect (OrderService.create — see docs/api.md's
    // "POST /api/v1/orders" section) — so this endpoint's only real callers
    // are staff, e.g. opening a comanda manually before any order exists.
    @PostMapping("/{number}/open")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER, UserRole.ATTENDANT})
    public CommandResponse open(@PathVariable int number) {
        Command command = commandService.open(number);
        return CommandResponse.from(command);
    }

    // Same POST-not-PATCH reasoning as open() above. No payment/fiscal
    // validation yet (FARELO-034) — that's Epic 10/FARELO-143.
    @PostMapping("/{number}/close")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER, UserRole.CASHIER})
    public CommandResponse close(@PathVariable int number) {
        Command command = commandService.close(number);
        return CommandResponse.from(command);
    }

}
