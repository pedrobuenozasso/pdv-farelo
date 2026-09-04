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
 * {@code /api/v1/commands} — {@code GET /{number}} (FARELO-032) and {@code
 * POST .../open} (FARELO-033). {@link #open} requires a staff role as of
 * FARELO-124; {@link #findByNumber} stays deliberately unprotected — see
 * its own javadoc.
 *
 * <p><b>{@code POST .../close} (FARELO-034) no longer lives here.</b> It
 * moved to {@code com.farelo.api.payment.web.PaymentController#close}
 * (FARELO-143, "Validar total pago antes de fechar") — closing now requires
 * validating total paid against total owed, a cross-domain check ({@code
 * payment} + {@code ordering}) that this class cannot perform without
 * {@code command.web} taking on a dependency in the wrong direction (see
 * {@code PaymentController}'s javadoc and {@code
 * PaymentService#closeCommand(int)}'s javadoc for the full reasoning). The
 * URL is unchanged ({@code POST /api/v1/commands/{number}/close}), same
 * role list ({@code ADMIN}/{@code MANAGER}/{@code CASHIER}), same response
 * shape ({@link CommandResponse}) — only the controller class changed.
 * {@code CommandService#close(int)} itself is untouched, still the
 * low-level status-transition primitive {@code PaymentService#closeCommand}
 * delegates to.
 *
 * <p>{@link #open}, unlike the former {@code close}, just marks a comanda
 * as being used (a customer sat down, staff starts their tab) and carries
 * no money implication, so {@code ATTENDANT} (table service) does this
 * routinely, same as {@code CASHIER}. {@code ADMIN}/{@code MANAGER} are
 * added too, same "can always do what any staff role can" reasoning
 * FARELO-123 already used for {@code CategoryController}/{@code
 * ProductController}.
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

}
