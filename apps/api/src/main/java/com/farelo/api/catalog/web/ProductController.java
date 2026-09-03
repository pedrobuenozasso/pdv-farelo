package com.farelo.api.catalog.web;

import com.farelo.api.catalog.Product;
import com.farelo.api.catalog.ProductService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.auth.AuthenticatedPrincipal;
import com.farelo.api.security.rbac.RequireRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/products} — {@code POST}/{@code PUT} (FARELO-014/016)
 * require {@link UserRole#ADMIN}/{@link UserRole#MANAGER} as of FARELO-123:
 * creating/editing a sellable product (price, category, availability,
 * production station) is the textbook "Admin → Produtos" module (prompt
 * mestre seção 21). {@code ADMIN}+{@code MANAGER} rather than {@code ADMIN}
 * alone: a shift manager routinely needs to 86 an item, flip
 * {@code availableOnMenu}/{@code availableOnPos}, or adjust a price without
 * waiting on the owner/admin account — the same "back-office staff, not the
 * top role alone" judgment call {@code UserController} makes differently
 * (see its javadoc) precisely because that controller can grant/escalate
 * roles and this one cannot. Not split further between {@code POST} and
 * {@code PUT} (unlike {@code UserController}'s read/write split) — both are
 * the same "author the menu" operation, just create vs. edit of the same
 * resource, so there is no natural narrower role for one but not the other.
 *
 * <p>{@link #list()} is deliberately left <b>unannotated</b> — see its own
 * javadoc.
 *
 * <p><b>FARELO-126</b>: {@link #update} now also declares an {@link
 * AuthenticatedPrincipal} parameter — always populated here, since this
 * method is already {@link RequireRole}-protected — and forwards {@code
 * principal.userId()} into {@link ProductService#update} so a real price
 * change can be attributed to the caller in the {@code audit} domain. See
 * that method's javadoc for the actor-resolution and price-delta-detection
 * design.
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        Product product = productService.create(
                request.name(),
                request.description(),
                request.price(),
                request.categoryId(),
                request.imageUrl(),
                request.availableOnMenu(),
                request.availableOnPos(),
                request.productionStation());

        URI location = uriComponentsBuilder
                .path("/api/v1/products/{id}")
                .buildAndExpand(product.getId())
                .toUri();

        return ResponseEntity.created(location).body(ProductResponse.from(product));
    }

    // No categoryId (or other) filter yet — YAGNI, no consumer (Admin/PDV)
    // asking for it yet. Add a query param here if/when one does.
    //
    // FARELO-123: deliberately NOT @RequireRole-protected, same reasoning
    // as CategoryController#list() — GET /api/v1/products is a direct
    // dependency of the anonymous customer-facing "Cardápio QR"
    // (FARELO-043), which has no login of any kind. The Admin surface for
    // products is authoring them (create()/update()), not reading them.
    @GetMapping
    public List<ProductResponse> list() {
        return productService.listAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    // AuthenticatedPrincipal (FARELO-126): this method is @RequireRole-
    // protected, so RoleAuthorizationInterceptor always populates one before
    // this handler runs (see AuthenticatedPrincipalArgumentResolver's
    // javadoc). Only principal.userId() is forwarded — ProductService
    // resolves it to a real User (and decides whether a price change even
    // happened) itself; see ProductService#update's javadoc for why that
    // decision lives there, not here.
    @PutMapping("/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public ProductResponse update(
            @PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest request, AuthenticatedPrincipal principal) {
        Product product = productService.update(
                id,
                request.name(),
                request.description(),
                request.price(),
                request.categoryId(),
                request.imageUrl(),
                request.active(),
                request.availableOnMenu(),
                request.availableOnPos(),
                request.productionStation(),
                principal.userId());

        return ProductResponse.from(product);
    }

}
