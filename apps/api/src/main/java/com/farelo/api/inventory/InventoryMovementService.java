package com.farelo.api.inventory;

import com.farelo.api.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * FARELO-093 gave this class its first (and until now, only) method,
 * {@link #listByIngredient(UUID)}. FARELO-094 ("Criar entrada manual de
 * estoque") added {@link #create(UUID, BigDecimal)}: the manual-entry
 * producer of {@link InventoryMovement} rows this domain was missing — see
 * {@link InventoryMovement}'s javadoc and {@link InventoryMovementType}'s
 * javadoc for why {@code PURCHASE} was always earmarked for that ticket.
 * FARELO-095 ("Calcular saldo do ingrediente") added {@link
 * #getBalance(UUID)}, reading the ledger this class already owns rather
 * than mutating it. FARELO-096 ("Consumir receita ao criar pedido") added
 * {@link #consumeForOrder(UUID, List)}, the second real producer. FARELO-097
 * ("Implementar idempotência da baixa de estoque") does not add a new
 * method — it changes {@link #consumeForOrder(UUID, List)} itself to be
 * safe to call more than once for the same order; see that method's own
 * javadoc for the full design. FARELO-098 ("Criar movimento de perda")
 * adds {@link #recordLoss(UUID, BigDecimal)}, the third real producer:
 * a human recording that stock was lost (spoilage/breakage/theft — not a
 * sale), mirroring {@link #create(UUID, BigDecimal)}'s validation shape
 * with a different type and a negated sign. FARELO-100/101 ("Publicar
 * STOCK_LOW"/"Publicar OUT_OF_STOCK") do not add a new method either — they
 * add a threshold check, {@link #publishStockThresholdEventIfNeeded(Ingredient)},
 * called after every stock-<em>reducing</em> movement this class writes
 * ({@link #recordLoss(UUID, BigDecimal)} and {@link #consumeForOrder(UUID, List)}
 * — deliberately <b>not</b> {@link #create(UUID, BigDecimal)}, since a
 * {@code PURCHASE} only ever increases stock and can never newly cross into
 * low/out-of-stock territory). See that method's own javadoc for the full
 * STOCK_LOW-vs-OUT_OF_STOCK design.
 */
@Service
public class InventoryMovementService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final IngredientService ingredientService;
    private final RecipeRepository recipeRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final OutboxPublisher outboxPublisher;

    public InventoryMovementService(
            InventoryMovementRepository inventoryMovementRepository,
            IngredientService ingredientService,
            RecipeRepository recipeRepository,
            RecipeItemRepository recipeItemRepository,
            OutboxPublisher outboxPublisher) {
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.ingredientService = ingredientService;
        this.recipeRepository = recipeRepository;
        this.recipeItemRepository = recipeItemRepository;
        this.outboxPublisher = outboxPublisher;
    }

    /**
     * Records a manual stock entry: a human (e.g. a manager) confirming that
     * stock physically arrived. Always produces a {@code PURCHASE} row with
     * a positive {@code quantity} — {@code type} is never a parameter here,
     * deliberately (see {@code InventoryMovementRequest}'s javadoc for why
     * this endpoint doesn't let a client pick the movement type). Validates
     * the ingredient exists first (404 {@link IngredientNotFoundException}),
     * same reasoning/order as {@link #listByIngredient(UUID)} and {@link
     * RecipeItemService#create}. {@code quantity > 0} is enforced by
     * {@code @Positive} on the request DTO before this method ever runs, not
     * re-checked here — same division of labor as {@link
     * RecipeItemService#create} trusting its own {@code @Positive} request
     * field.
     *
     * <p>{@code @Transactional} even though this is a single {@code save}:
     * matches every other mutating method in this domain ({@link
     * RecipeItemService#create}, {@link IngredientService#update}) rather
     * than relying on Spring Data's own per-method transaction, so a future
     * addition here (e.g. touching a running balance) doesn't silently need
     * a second annotation added.
     */
    @Transactional
    public InventoryMovement create(UUID ingredientId, BigDecimal quantity) {
        Ingredient ingredient = ingredientService.getById(ingredientId);
        return inventoryMovementRepository.save(
                new InventoryMovement(ingredient, quantity, InventoryMovementType.PURCHASE));
    }

    /**
     * FARELO-098 ("Criar movimento de perda"): records a stock LOSS —
     * spoilage, breakage, theft, or any other stock reduction that isn't a
     * sale (see {@link InventoryMovementType}'s javadoc for {@code LOSS}'s
     * own description). A human (e.g. a manager) reports {@code quantity}
     * as a POSITIVE magnitude — "how much was lost" — and this method is
     * the one place that negates it before constructing the row; the
     * request DTO never encodes the sign itself (see {@code
     * com.farelo.api.inventory.web.InventoryLossRequest}'s javadoc for the
     * full reasoning, which mirrors why {@link InventoryMovementRequest}
     * doesn't let a client encode {@code PURCHASE}'s sign either). Same
     * "each producer decides its own sign when constructing the row"
     * division of labor documented on {@link
     * InventoryMovement#getQuantity()}.
     *
     * <p>Same "ingredient exists first" validation and ordering as {@link
     * #create(UUID, BigDecimal)} (404 {@link IngredientNotFoundException}
     * before anything else). {@code quantity > 0} is enforced by {@code
     * @Positive} on the request DTO before this method ever runs, not
     * re-checked here — same division of labor as {@link #create(UUID,
     * BigDecimal)}.
     *
     * <p>No {@code orderId}: a loss is never order-sourced — see {@link
     * InventoryMovement}'s javadoc ("orderId" section) for why only {@code
     * ORDER_CONSUMPTION} (and plausibly {@code RETURN}/{@code
     * CANCELLATION}) are expected to ever set that column. Uses the
     * three-argument {@link InventoryMovement} constructor, same as {@link
     * #create(UUID, BigDecimal)}.
     *
     * <p>{@code @Transactional}: same reasoning as {@link #create(UUID,
     * BigDecimal)} — matches every other mutating method in this domain
     * rather than relying on Spring Data's own per-method transaction.
     *
     * <p><b>FARELO-100/101</b>: after writing the row, checks whether {@code
     * ingredient}'s resulting balance is now low/out of stock and, if so,
     * publishes the corresponding outbox event — see {@link
     * #publishStockThresholdEventIfNeeded(Ingredient)} for the full design.
     * Still inside this same transaction (unchanged {@code @Transactional}
     * above), same "one more thing happens after the domain write, same
     * transaction" shape already established by {@code OutboxPublisher}'s
     * other integrations ({@code OrderService#create}/{@code #markAsReady}).
     */
    @Transactional
    public InventoryMovement recordLoss(UUID ingredientId, BigDecimal quantity) {
        Ingredient ingredient = ingredientService.getById(ingredientId);
        InventoryMovement movement = inventoryMovementRepository.save(
                new InventoryMovement(ingredient, quantity.negate(), InventoryMovementType.LOSS));
        publishStockThresholdEventIfNeeded(ingredient);
        return movement;
    }

    // Validates the ingredient exists first (404 INGREDIENT_NOT_FOUND) so
    // that listing movements for an unknown ingredient id is distinguishable
    // from a real ingredient that simply has no movements yet (both would
    // otherwise return an identical empty list) — same reasoning as
    // RecipeItemService#listByRecipe.
    public List<InventoryMovement> listByIngredient(UUID ingredientId) {
        ingredientService.getById(ingredientId);
        return inventoryMovementRepository.findByIngredientIdOrderByCreatedAtAsc(ingredientId);
    }

    /**
     * FARELO-095 ("Calcular saldo do ingrediente"): an ingredient's current
     * stock balance — the sum of every {@link InventoryMovement} row
     * recorded for it (prompt mestre seção 13: "O saldo deve ser
     * rastreável (derivado do ledger, nunca editado diretamente)"). Reuses
     * {@link InventoryMovementRepository#sumQuantityByIngredientId} exactly
     * as FARELO-093 laid it down for this purpose — the database already
     * computes {@code COALESCE(SUM(quantity), 0)}, so re-summing in Java
     * would be a slower, riskier duplicate of the same logic (and the
     * project convention, per AGENTS.md, is {@code BigDecimal} end to end
     * for exactly this kind of quantity, never a primitive re-derivation).
     *
     * <p>Same "ingredient exists first" validation and ordering as {@link
     * #listByIngredient(UUID)} (404 {@link IngredientNotFoundException}
     * before anything else): a balance of {@code 0} because the ingredient
     * genuinely has no movements yet must stay distinguishable from an
     * ingredient id that doesn't exist at all — same reasoning {@link
     * #listByIngredient(UUID)} already documents for an empty movement
     * list.
     */
    public IngredientBalance getBalance(UUID ingredientId) {
        Ingredient ingredient = ingredientService.getById(ingredientId);
        BigDecimal balance = inventoryMovementRepository.sumQuantityByIngredientId(ingredientId);
        return new IngredientBalance(ingredient, balance);
    }

    /**
     * FARELO-096 ("Consumir receita ao criar pedido", prompt mestre seção
     * 16): called by {@code com.farelo.api.ordering.OrderService#create}
     * right after an order and its items are persisted (so {@code orderId}
     * is available), in the same transaction. For each sold item, looks up
     * the product's active {@link Recipe} (if any — see below) and, for
     * every ingredient any of the order's recipes consume, writes at most
     * one {@code ORDER_CONSUMPTION} {@link InventoryMovement}.
     *
     * <p><b>Quantity math</b>: {@link RecipeItem#getQuantity()} is "how much
     * of this ingredient for ONE unit of the product" (its own javadoc).
     * For each {@link OrderItemConsumption}/{@link RecipeItem} pair, that
     * quantity is multiplied by how many units of the product were sold
     * ({@link OrderItemConsumption#quantity()}) and negated — stock going
     * out, same sign convention documented on {@link
     * InventoryMovement#getQuantity()}. {@code BigDecimal} throughout
     * (AGENTS.md): {@code RecipeItem.quantity} (scale 3) is multiplied by
     * an exact integer count via {@link BigDecimal#valueOf(long)}, so each
     * term keeps the same scale — no rounding, no {@code double}
     * anywhere.
     *
     * <p><b>Products without a recipe are silently skipped, not an
     * error</b>: not every product has one yet (a recipe is optional per
     * {@link Recipe}'s own design — {@code Product} carries no required
     * back-reference to it). {@link RecipeRepository#findByProductIdAndActiveTrue}
     * returning empty for a given item is simply "nothing to consume for
     * this line", the same way {@code Recipe}/{@code RecipeItem}'s own
     * javadoc already anticipated for FARELO-096.
     *
     * <p><b>No stock-sufficiency check</b> (prompt mestre seção 16 doesn't
     * ask for one here — going negative is allowed for now, plausibly
     * FARELO-099 "estoque mínimo"'s concern later, not this one's).
     *
     * <p><b>FARELO-097 — idempotency ("Implementar idempotência da baixa de
     * estoque")</b>: prompt mestre seção 16 gives the natural key
     * literally — "{@code ORDER_CONSUMPTION orderId=123 ingredientId=5} não
     * deve conseguir ser processado duas vezes" — i.e. the key is
     * {@code (type, orderId, ingredientId)}, <b>not</b>
     * {@code (type, orderId, productId, ingredientId)} or one row per
     * recipe/product line. Two design consequences follow from taking that
     * key literally, both implemented here:
     *
     * <ol>
     *   <li><b>Aggregation across recipe lines for the same ingredient
     *       within one order.</b> If two different products in the same
     *       order both consume the same ingredient (e.g. a latte and a
     *       cappuccino both using milk), every {@code RecipeItem} match for
     *       that ingredient across every {@link OrderItemConsumption} is
     *       summed into a single {@code BigDecimal} <em>before</em>
     *       anything is written — see the {@code quantityByIngredientId}
     *       map below. This is not an incidental workaround: it is required
     *       by the key itself. Without it, an order that legitimately uses
     *       the same ingredient in two products would try to write two
     *       {@code ORDER_CONSUMPTION} rows for the same
     *       {@code (orderId, ingredientId)} pair on a perfectly ordinary
     *       <em>first</em> call — which the partial unique index (see
     *       {@code V23__add_inventory_movement_order_consumption_unique_index.sql})
     *       would then reject as if it were a double-processing attempt,
     *       when it never was one.</li>
     *   <li><b>A per-ingredient pre-check, not a per-order one.</b> Before
     *       writing the aggregated row for a given ingredient, this method
     *       calls {@link InventoryMovementRepository#existsByTypeAndOrderIdAndIngredientId}
     *       for {@code (ORDER_CONSUMPTION, orderId, ingredientId)}; if a row
     *       already exists, that ingredient is skipped — nothing is
     *       written, and nothing is added to the returned list. Checking
     *       per ingredient (rather than e.g. "does this order have
     *       <em>any</em> {@code ORDER_CONSUMPTION} rows yet, and if so skip
     *       the whole call") is what makes a <b>partial-completion retry
     *       safe and self-healing</b>: if a previous call wrote ingredient
     *       A's row and then crashed (process killed, DB connection lost,
     *       whatever) before reaching ingredient B, a retry with the exact
     *       same arguments recomputes the same aggregated quantities,
     *       finds A already recorded (skips it — no double deduction) and
     *       finds B still missing (writes it — no permanently-undeducted
     *       ingredient). Neither of the two failure modes the ticket calls
     *       out happens: it does not silently no-op the entire retry
     *       (which would leave B never deducted), and it does not error out
     *       unrecoverably (which would leave the caller with no path to
     *       completion). A call where every ingredient was already recorded
     *       is therefore a full no-op: it performs the same lookups,
     *       writes nothing, and returns an empty list — <em>idempotent
     *       success</em>, not an exception, because from the caller's
     *       perspective "already fully consumed" and "just consumed" are
     *       the same outcome (the order's stock has been deducted exactly
     *       once).</li>
     * </ol>
     *
     * <p><b>Return value contract, updated by this ticket</b>: this method
     * returns only the {@link InventoryMovement} rows it <em>actually wrote
     * during this call</em> — not every {@code ORDER_CONSUMPTION} row that
     * exists for the order (that's what {@link
     * InventoryMovementRepository#findByOrderId} is for). A second call for
     * an already-fully-consumed order therefore returns an empty list, and
     * a partial-completion retry returns only the newly-completed
     * ingredients' rows. This is safe for the one real caller today,
     * {@code OrderService#create}, which discards the return value
     * entirely (order creation never calls this twice for the same order —
     * see the class-level note below on why this guard is defensive
     * infrastructure for a future caller, not something FARELO-096's own
     * call site needs); a future retry/replay caller that does care can
     * rely on this contract to tell "I completed N new movements" apart
     * from "there was nothing left to do".
     *
     * <p><b>Why a pre-check instead of catching the DB constraint
     * violation</b>: this mirrors the precedent already established by
     * {@link RecipeService#create(UUID)} and
     * {@link RecipeItemService#create(UUID, UUID, BigDecimal)} in this same
     * package — both check first via a repository query (fail fast, no DB
     * round-trip cost of a failed {@code INSERT}) and rely on the DB
     * constraint only as the backstop for a genuine race between two
     * concurrent calls, without wrapping the {@code save} in a
     * {@code try/catch}. This method follows the same division of labor:
     * the pre-check handles the expected case (a sequential retry/replay,
     * which is what FARELO-097 exists for), and the partial unique index
     * handles the rare case (two concurrent calls for the same order
     * racing past the pre-check before either commits) by letting the
     * second {@code save} throw — uncaught, exactly like
     * {@code RecipeItemService#create} does for its own
     * {@code UNIQUE(recipe_id, ingredient_id)} constraint.
     *
     * <p><b>Whether a constraint violation should roll back order
     * creation, and why this guard is mainly defensive infrastructure for a
     * future caller</b>: this method still runs inside {@code
     * OrderService#create}'s {@code @Transactional} method (see
     * {@code @Transactional} below — unchanged from FARELO-096). If a
     * genuine race did trip the DB constraint here, the exception
     * propagates uncaught and the whole order-creation transaction rolls
     * back with it — same "one more thing happens after order creation,
     * same transaction, all-or-nothing" shape {@code OutboxPublisher}
     * already established (FARELO-060), deliberately not swallowed. That
     * said, order creation itself is not expected to ever be the caller
     * that trips this constraint: {@code OrderService#create} always
     * inserts a brand-new {@code Order} row first, so there is no "same
     * order twice" scenario reachable from that path alone — a fresh
     * {@code orderId} can never already have {@code ORDER_CONSUMPTION}
     * rows before its own first {@code consumeForOrder} call. This
     * idempotency guard is therefore defensive infrastructure for a
     * <em>future</em> caller that legitimately retries/replays consumption
     * for an <b>existing</b> {@code orderId} — e.g. a retried HTTP request
     * hitting some future endpoint that re-triggers consumption, a bug
     * causing a duplicate call, or a future replay/reconciliation
     * mechanism — not a scenario FARELO-096's own call site produces on its
     * own. Documented here so a future reader doesn't mistake this for
     * dead code.
     *
     * <p><b>{@code @Transactional}</b>: called from within {@code
     * OrderService#create}'s own {@code @Transactional} method, so this
     * annotation mainly documents the requirement (Spring's default
     * propagation, {@code REQUIRED}, joins the caller's existing
     * transaction rather than starting a new one) — same "one more thing
     * happens after order creation, same transaction" shape already
     * established by that method's {@code OutboxPublisher} call
     * (FARELO-060): if writing a movement fails, the whole order creation
     * rolls back rather than leaving an order with a missing/partial stock
     * deduction.
     */
    @Transactional
    public List<InventoryMovement> consumeForOrder(UUID orderId, List<OrderItemConsumption> items) {
        // ingredientId -> ingredient (first one seen) / aggregated quantity
        // across every recipe line, for every product in this call, that
        // touches that ingredient. See this method's javadoc ("FARELO-097
        // — idempotency", point 1) for why aggregating before writing
        // anything is required by the (type, orderId, ingredientId) key
        // itself, not just a convenience. LinkedHashMap: no ordering
        // requirement from the DB/tests, just deterministic iteration for
        // readability/debugging (same non-reason every other Map in this
        // codebase's service layer would use it).
        Map<UUID, Ingredient> ingredientsById = new LinkedHashMap<>();
        Map<UUID, BigDecimal> quantityByIngredientId = new LinkedHashMap<>();

        for (OrderItemConsumption item : items) {
            Optional<Recipe> recipe = recipeRepository.findByProductIdAndActiveTrue(item.productId());
            if (recipe.isEmpty()) {
                continue;
            }

            List<RecipeItem> recipeItems = recipeItemRepository.findByRecipeId(recipe.get().getId());
            for (RecipeItem recipeItem : recipeItems) {
                Ingredient ingredient = recipeItem.getIngredient();
                BigDecimal delta = recipeItem.getQuantity()
                        .multiply(BigDecimal.valueOf(item.quantity()))
                        .negate();

                ingredientsById.putIfAbsent(ingredient.getId(), ingredient);
                quantityByIngredientId.merge(ingredient.getId(), delta, BigDecimal::add);
            }
        }

        List<InventoryMovement> movements = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> entry : quantityByIngredientId.entrySet()) {
            UUID ingredientId = entry.getKey();

            // FARELO-097 idempotency pre-check — see this method's javadoc
            // (point 2) for why this is per-ingredient, not per-order, and
            // why that's what makes a partial-completion retry safe.
            if (inventoryMovementRepository.existsByTypeAndOrderIdAndIngredientId(
                    InventoryMovementType.ORDER_CONSUMPTION, orderId, ingredientId)) {
                continue;
            }

            Ingredient ingredient = ingredientsById.get(ingredientId);
            InventoryMovement movement = new InventoryMovement(
                    ingredient,
                    entry.getValue(),
                    InventoryMovementType.ORDER_CONSUMPTION,
                    orderId);
            movements.add(inventoryMovementRepository.save(movement));

            // FARELO-100/101: only for ingredients this call actually wrote
            // a row for — an ingredient skipped by the idempotency pre-check
            // above already had its threshold checked (and, if applicable,
            // its event published) by whichever earlier call first wrote
            // that row; re-checking it again here on every retry/no-op call
            // would be redundant, not a new crossing. See
            // #publishStockThresholdEventIfNeeded(Ingredient) for the full
            // design.
            publishStockThresholdEventIfNeeded(ingredient);
        }

        return movements;
    }

    /**
     * FARELO-100/101 ("Publicar STOCK_LOW"/"Publicar OUT_OF_STOCK", prompt
     * mestre seção 17/29): after a stock-<em>reducing</em> movement has been
     * written for {@code ingredient}, re-reads its ledger-derived balance
     * (same query {@link #getBalance(UUID)} uses,
     * {@link InventoryMovementRepository#sumQuantityByIngredientId}) and
     * publishes an outbox event if that balance is now low or out of stock.
     * Called by {@link #recordLoss(UUID, BigDecimal)} and {@link
     * #consumeForOrder(UUID, List)} — deliberately <b>not</b> by {@link
     * #create(UUID, BigDecimal)}: a {@code PURCHASE} row is always positive
     * (see that method's javadoc), so it only ever increases a balance and
     * can never newly cross <em>into</em> low/out-of-stock territory. Not
     * {@code @Transactional} itself — it always runs as part of the caller's
     * already-{@code @Transactional} method, same "one write, same
     * transaction" shape as every other mutation in this class.
     *
     * <p><b>Only three of the seven event names prompt mestre seção 29
     * lists are in scope</b>: {@code STOCK_LOW} and {@code OUT_OF_STOCK} —
     * literally the two tickets this method exists for (FARELO-100/101).
     * {@code STOCK_CRITICAL} (seção 17/29 also names it, alongside a
     * matching {@code Ingredient.criticalStock} field) is deliberately
     * <b>not</b> published here: FARELO-099 ("Criar estoque mínimo")
     * explicitly scoped {@code criticalStock} out as "ticket futuro, não
     * numerado ainda" (see {@code Ingredient.minimumStock}'s javadoc and
     * docs/domain-model.md's FARELO-099 entry), and no numbered ticket in
     * the current roadmap (docs/PROMPT_MESTRE.md §47, Epic 7) claims {@code
     * STOCK_CRITICAL} — only FARELO-100/101 are numbered, for the other two
     * events. There is no {@code criticalStock} field to even compare
     * against today, so implementing {@code STOCK_CRITICAL} here would mean
     * inventing both a new column and a threshold semantic the ticket never
     * asked for — exactly the kind of unscheduled-work anticipation this
     * codebase's existing precedent avoids (see e.g. {@code Ingredient}'s
     * own javadoc on why {@code criticalStock} wasn't added alongside {@code
     * minimumStock}). Left out on purpose, not an oversight.
     *
     * <p><b>{@code isOutOfStock()} takes precedence over {@code
     * isBelowMinimum()}</b>: at most <em>one</em> event is published per
     * call, never both, even though {@link IngredientBalance#isOutOfStock()}
     * and {@link IngredientBalance#isBelowMinimum()} can both be {@code
     * true} for the same balance (see {@code isOutOfStock()}'s javadoc,
     * "Interaction with isBelowMinimum()"). {@code OUT_OF_STOCK} is checked
     * first and, if true, is published instead of {@code STOCK_LOW} — it is
     * strictly the more severe, more specific fact ("nothing left" versus
     * "less than we'd like"), and a future consumer (FARELO-113) reacting to
     * both event types would otherwise receive two events describing the
     * same single movement, one of them redundant. An ingredient whose
     * balance is below its minimum but still positive (not out of stock)
     * publishes {@code STOCK_LOW} only, as expected.
     *
     * <p><b>Publishes on every qualifying movement, not only on the
     * threshold <em>crossing</em></b>: if an ingredient is already below its
     * minimum (or already at/below zero) and another {@code LOSS}/{@code
     * ORDER_CONSUMPTION} movement pushes it further down, this method
     * publishes again — it does not try to detect "was the previous balance
     * already below the same threshold" and suppress a repeat. This is a
     * deliberate, judgment-call default, not an oversight: neither prompt
     * mestre seção 17 nor seção 29 says anything about deduplicating
     * repeated alerts, and detecting "was this already below threshold
     * before this movement" would require an extra query per movement (the
     * balance immediately before the just-written row) purely to support a
     * consumer that doesn't exist yet — {@code OutboxWorker#dispatch} has no
     * branch for {@code STOCK_LOW}/{@code OUT_OF_STOCK} today (by this
     * ticket's own explicit scope; reacting to these events, e.g. creating a
     * {@code Notification}, is FARELO-113, a separate future ticket). A
     * "publish on every crossing only" design is plausibly more correct
     * long-term (it would avoid flooding a future WhatsApp integration with
     * one message per sale once an ingredient is already known to be low),
     * but building that suppression logic now would be speculative — the
     * real shape of "already notified" (per-ingredient? per-day? until
     * restocked above threshold?) is exactly the kind of decision that
     * belongs to FARELO-113, the ticket that actually has a downstream
     * consumer to design it against. Every ticket up to FARELO-099 already
     * establishes this codebase's YAGNI convention (e.g. {@code
     * criticalStock} left out above); this follows the same discipline.
     */
    private void publishStockThresholdEventIfNeeded(Ingredient ingredient) {
        BigDecimal balance = inventoryMovementRepository.sumQuantityByIngredientId(ingredient.getId());
        IngredientBalance ingredientBalance = new IngredientBalance(ingredient, balance);

        if (ingredientBalance.isOutOfStock()) {
            outboxPublisher.publish(
                    "Ingredient", ingredient.getId(), "OUT_OF_STOCK", StockThresholdEvent.from(ingredient, balance));
        } else if (ingredientBalance.isBelowMinimum()) {
            outboxPublisher.publish(
                    "Ingredient", ingredient.getId(), "STOCK_LOW", StockThresholdEvent.from(ingredient, balance));
        }
    }

}
