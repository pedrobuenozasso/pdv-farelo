package com.farelo.api.catalog;

import com.farelo.api.audit.AuditLogService;
import com.farelo.api.security.User;
import com.farelo.api.security.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    // FARELO-126: action/entityType constants for the AuditLogService#record
    // call in update() below — plain String constants, not a shared enum,
    // per AuditLog's own javadoc ("Design decision 3": action/entityType are
    // an open, producer-defined vocabulary, not a closed set this class
    // shares with anything else).
    private static final String AUDIT_ACTION_PRICE_CHANGED = "PRICE_CHANGED";
    private static final String AUDIT_ENTITY_TYPE = "Product";

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            UserService userService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public Product create(
            String name, String description, BigDecimal price, UUID categoryId, String imageUrl,
            Boolean availableOnMenu, Boolean availableOnPos, ProductionStation productionStation) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        Product product = new Product(name, price, category);
        product.setDescription(description);
        product.setImageUrl(imageUrl);
        // availableOnMenu/availableOnPos default to true (see Product's field
        // initializers) when the request omits them — only override when the
        // caller explicitly sent a value.
        if (availableOnMenu != null) {
            product.setAvailableOnMenu(availableOnMenu);
        }
        if (availableOnPos != null) {
            product.setAvailableOnPos(availableOnPos);
        }
        // productionStation has no default to apply when absent (unlike the
        // two booleans above) — null is itself the correct "not assigned"
        // value (see Product's field javadoc), so it's set unconditionally.
        product.setProductionStation(productionStation);

        return productRepository.save(product);
    }

    public List<Product> listAll() {
        return productRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    }

    // Reused by both update() below and com.farelo.api.ordering.OrderService
    // (order creation needs to fetch a product by id, same 404 semantics).
    public Product getById(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Full-replace update (FARELO-016). {@code actorId} (FARELO-126,
     * resolved by the caller from {@code AuthenticatedPrincipal#userId()}
     * once RBAC actually authenticates this endpoint — see {@code
     * ProductController#update}) identifies who is making the change; it is
     * only ever looked up/used when the price actually changes — see below.
     *
     * <h2>FARELO-126 — auditing a price change</h2>
     *
     * The old price is captured from {@code product} <em>before</em> any
     * field is overwritten, then compared (via {@code BigDecimal#compareTo},
     * never {@code equals} — scale-insensitive, so {@code 12.50} vs {@code
     * 12.5} is correctly "unchanged", see AGENTS.md on money handling)
     * against the new {@code price} parameter <em>after</em> the save. Only
     * when they differ does this method resolve the actor
     * ({@link UserService#getById}) and call {@code AuditLogService#record}.
     *
     * <p><b>Why price-delta detection, not "audit every update"</b>: prompt
     * mestre seção 27 lists "preço, estoque, cancelamento, pagamento,
     * configuração fiscal, produto" as separate, distinct sensitive
     * categories — "preço" is called out on its own, not folded into
     * "produto" generically, so this ticket (literally "Auditar alteração de
     * PREÇO") audits the price delta specifically, not every field this
     * full-replace {@code PUT} happens to touch (name, category,
     * availability, production station). Auditing indiscriminately would
     * also be actively misleading: a caller re-submitting the exact same
     * price (or changing only, say, {@code availableOnMenu}) would produce
     * an audit row whose {@code previousValue}/{@code newValue} are
     * identical — noise a reviewer of the audit trail would have to learn to
     * ignore, undermining the trail's whole purpose. See
     * docs/domain-model.md's FARELO-126 subsection for the full writeup.
     *
     * <p><b>Why this lookup/audit-recording lives here, not in {@code
     * ProductController}</b>: this codebase's established split keeps
     * controllers thin (parse the request, forward ids) and pushes id →
     * entity resolution and business decisions into services — this method
     * already does exactly that for {@code categoryId} → {@link Category}
     * two lines below, and every cross-domain lookup elsewhere in this
     * codebase goes through the other domain's service, never its
     * repository directly (e.g. {@code OrderService}/{@code RecipeService}
     * both depend on {@code ProductService}, not {@code ProductRepository}).
     * "Did the price actually change, and if so, resolve the actor and
     * record it" is exactly that shape of decision — it belongs beside the
     * rest of this method's business logic, not duplicated into the
     * controller. {@code ProductController#update} only extracts {@code
     * principal.userId()} and forwards it, same "thin controller" role it
     * already plays for every other field on this request.
     */
    @Transactional
    public Product update(
            UUID id, String name, String description, BigDecimal price, UUID categoryId, String imageUrl,
            boolean active, boolean availableOnMenu, boolean availableOnPos, ProductionStation productionStation,
            UUID actorId) {
        Product product = getById(id);
        BigDecimal previousPrice = product.getPrice();

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));

        product.setName(name);
        product.setDescription(description);
        product.setPrice(price);
        product.setCategory(category);
        product.setImageUrl(imageUrl);
        product.setActive(active);
        product.setAvailableOnMenu(availableOnMenu);
        product.setAvailableOnPos(availableOnPos);
        // Unconditional, same as create(): a PUT is a full replace, and null
        // here is a legitimate, intentional value (clear a previously
        // assigned station) — not a "field omitted" placeholder to guard.
        product.setProductionStation(productionStation);

        Product saved = productRepository.save(product);

        // BigDecimal#compareTo, not #equals — see javadoc above.
        if (previousPrice.compareTo(price) != 0) {
            recordPriceChange(actorId, saved.getId(), previousPrice, price);
        }

        return saved;
    }

    private void recordPriceChange(UUID actorId, UUID productId, BigDecimal previousPrice, BigDecimal newPrice) {
        User actor = userService.getById(actorId);
        auditLogService.record(
                actor,
                AUDIT_ACTION_PRICE_CHANGED,
                AUDIT_ENTITY_TYPE,
                productId,
                serializePrice(previousPrice),
                serializePrice(newPrice));
    }

    // Same serialize-and-wrap pattern as PrintJobService#serialize:
    // ProductPriceSnapshot is a simple record built from an already-valid
    // BigDecimal, so a JsonProcessingException here would mean Jackson
    // genuinely can't serialize it — an invariant violation, not an
    // expected runtime condition.
    private String serializePrice(BigDecimal price) {
        try {
            return objectMapper.writeValueAsString(new ProductPriceSnapshot(price));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize product price snapshot", e);
        }
    }

}
