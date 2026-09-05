package com.farelo.api.catalog.web;

import com.farelo.api.catalog.Category;
import com.farelo.api.catalog.CategoryService;
import com.farelo.api.security.UserRole;
import com.farelo.api.security.rbac.RequireRole;
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
 * {@code /api/v1/categories} — {@code POST} (FARELO-012) requires
 * {@link UserRole#ADMIN}/{@link UserRole#MANAGER} as of FARELO-123: creating
 * a menu category is back-office catalog management, the same "Admin"
 * surface as {@code ProductController}'s write endpoints. See that
 * controller's javadoc for the shared reasoning (why {@code ADMIN}+
 * {@code MANAGER} rather than {@code ADMIN} alone, and why the two roles
 * aren't split further by operation).
 *
 * <p>{@link #list()} is deliberately left <b>unannotated</b> — see its own
 * javadoc.
 */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    @RequireRole({UserRole.ADMIN, UserRole.MANAGER})
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryRequest request,
            UriComponentsBuilder uriComponentsBuilder) {
        Category category = categoryService.create(request.name(), request.description(), request.sortOrder());

        URI location = uriComponentsBuilder
                .path("/api/v1/categories/{id}")
                .buildAndExpand(category.getId())
                .toUri();

        return ResponseEntity.created(location).body(CategoryResponse.from(category));
    }

    // FARELO-123: deliberately NOT @RequireRole-protected, unlike create()
    // above. Prompt mestre EPIC 3 (FARELO-042) makes GET /api/v1/categories
    // a dependency of the customer-facing "Cardápio QR" (pedido.farelo.com.br)
    // — an anonymous visitor scanning a table QR code, with no login/account
    // of any kind, needs to read the category list to render the menu.
    // Protecting this endpoint would not narrow it to some other internal
    // role; it would break that public flow outright, which is out of this
    // ticket's "Admin surface" scope entirely (see also PDV/kitchen's own
    // read access, FARELO-124's concern, not decided here). The Admin
    // surface for categories is authoring them (create()), not reading them.
    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.listAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

}
